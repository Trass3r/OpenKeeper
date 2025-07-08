import statistics, sys, csv
import renderdoc as rd
from collections import defaultdict

def walk_actions(action, group_stack, event_group_map):
    # Push marker: push group name onto stack
    if action.flags & rd.ActionFlags.PushMarker:
        group_stack = group_stack + [action.customName]  # copy for recursion
    # For all actions, record the current group stack
    event_group_map[action.eventId] = group_stack
    # Recurse into children
    for child in action.children:
        walk_actions(child, group_stack, event_group_map)
    # Pop marker: handled implicitly by recursion (since each call gets its own list)

def build_event_group_map(controller):
    event_group_map = {}
    for root in controller.GetRootActions():
        walk_actions(root, [], event_group_map)
    return event_group_map

def iterDraw(d, actions):
    actions[d.eventId] = d
    for child in d.children:
        iterDraw(child, actions)

def list_drawcalls_with_event_duration(controller, num_runs=5):
    actions = {}
    for root in controller.GetRootActions():
        iterDraw(root, actions)

    # Build group stack map
    event_group_map = build_event_group_map(controller)

    # Enumerate available counters
    counters = controller.EnumerateCounters()
    if rd.GPUCounter.EventGPUDuration not in counters:
        raise RuntimeError("Implementation doesn't support EventGPUDuration counter")

    durationDesc = controller.DescribeCounter(rd.GPUCounter.EventGPUDuration)
    all_results = []
    for run in range(num_runs):
        print(f"Fetching counters run {run+1}/{num_runs}...")
        results = controller.FetchCounters([rd.GPUCounter.EventGPUDuration])
        all_results.append(results)

    event_durations = defaultdict(list)
    for run_results in all_results:
        for r in run_results:
            event_durations[r.eventId].append(r.value.d * 1e6)

    sdfile = controller.GetStructuredFile()
    with open("drawcalls.csv", "w", newline="") as csvfile:
        writer = csv.writer(csvfile)
        writer.writerow(["EventId", "MedianDuration_us", "AvgDuration_us", "Group", "DrawCall"])
        for eventId in sorted(event_durations.keys()):
            action = actions.get(eventId, None)
            if action is None:
                continue
            if not (action.flags & rd.ActionFlags.Drawcall):
                continue

            durations = event_durations[eventId]
            avg_duration = sum(durations) / len(durations)
            median_duration = statistics.median(durations)

            params = []
            if hasattr(action, "numIndices") and action.numIndices > 0:
                params.append(str(action.numIndices))
            if hasattr(action, "numInstances") and action.numInstances > 1:
                params.append(f"instances={action.numInstances}")
            param_str = "(" + ", ".join(params) + ")" if params else "()"
            name = f"{action.GetName(sdfile)[:-2]}{param_str}"  # remove the default '()'

            debug_group_stack = event_group_map.get(eventId, [])
            group_str = "/".join(debug_group_stack) if debug_group_stack else ""

            writer.writerow([eventId, f"{median_duration:.3f}", f"{avg_duration:.3f}", group_str, name])
    print("Draw call CSV written to drawcalls.csv")

def loadCapture(filename):
    cap = rd.OpenCaptureFile()
    result = cap.OpenFile(filename, '', None)
    if result != rd.ResultCode.Succeeded:
        raise RuntimeError("Couldn't open file: " + str(result))
    if not cap.LocalReplaySupport():
        raise RuntimeError("Capture cannot be replayed")
    result,controller = cap.OpenCapture(rd.ReplayOptions(), None)
    if result != rd.ResultCode.Succeeded:
        raise RuntimeError("Couldn't initialise replay: " + str(result))
    return cap,controller

if 'pyrenderdoc' in globals():
    pyrenderdoc.Replay().BlockInvoke(lambda c: list_drawcalls_with_event_duration(c))
else:
    rd.InitialiseReplay(rd.GlobalEnvironment(), [])
    if len(sys.argv) <= 1:
        print(f'Usage: python3 {sys.argv[0]} filename.rdc')
        sys.exit(0)
    cap,controller = loadCapture(sys.argv[1])
    list_drawcalls_with_event_duration(controller)
    controller.Shutdown()
    cap.Shutdown()
    rd.ShutdownReplay()
