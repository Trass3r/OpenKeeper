/*
 * Copyright (C) 2014-2020 OpenKeeper
 *
 * OpenKeeper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenKeeper is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenKeeper.  If not, see <http://www.gnu.org/licenses/>.
 */
package toniarts.openkeeper.tools.convert.conversion;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import toniarts.openkeeper.tools.convert.AssetsConverter;
import toniarts.openkeeper.tools.convert.conversion.graph.BreadthFirstTraverser;
import toniarts.openkeeper.tools.convert.conversion.graph.Graph;
import toniarts.openkeeper.tools.convert.conversion.graph.TaskNode;

/**
 * Handles all your asset conversion needs. The dataflow and multithreading.
 * Does not validate the work flow i.e. check for cyclic dependencies etc. Very
 * naive implementation, but should be sufficient for our simple needs.
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public class ConversionTaskManager {

    private static final Logger LOGGER = System.getLogger(ConversionTaskManager.class.getName());
    private static final int MAX_THREADS = Runtime.getRuntime().availableProcessors();

    private final ExecutorService executorService;
    private final AtomicInteger tasksRunning = new AtomicInteger();
    private int tasksToRun = 0;
    private final Map<TaskNode, Future> runningTasks = new HashMap<>();
    private final Map<AssetsConverter.ConvertProcess, TaskNode> taskNodes = new LinkedHashMap<>();
    private final Graph<TaskNode> graph = new Graph<>();
    private boolean failure = false;

    public ConversionTaskManager() {
        this.executorService = Executors.newFixedThreadPool(MAX_THREADS, new ThreadFactory() {

            private final AtomicInteger threadIndex = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "AssetConversionTask_" + threadIndex.incrementAndGet());
            }

        });
    }

    public void addTask(AssetsConverter.ConvertProcess conversion, IConversionTaskProvider task, boolean conversionNeeded) {
        taskNodes.put(conversion, new TaskNode(conversion.ordinal(), conversion.name(), task, !conversionNeeded));
    }

    /**
     * Starts all the assigned tasks. Blocks until complete
     *
     * @return true if the conversion process was successful
     */
    public boolean executeTasks() {
        createTaskGraph();

        // If we didn't have anything, just quit
        if (tasksToRun == 0) {
            return true;
        }

        // Get root nodes to start the processing
        List<TaskNode> rootNodes = graph.getRootNodes();

        // Prioritize tasks so that have higher amount of dependendant (initial) tasks get the priority
        sortNodesChildCountDesc(rootNodes);
            synchronized (graph) {
                for (TaskNode node : rootNodes) {
                    new TaskExecuterGraphTraverser().traverse(node);
                }
            }

        try {
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.HOURS);
        } catch (InterruptedException ex) {
            LOGGER.log(Level.ERROR, "Conversion tasks failed to complete!", ex);
        }

        return !failure;
    }

    private void createTaskGraph() {
        tasksToRun = 0;
        for (Entry<AssetsConverter.ConvertProcess, TaskNode> node : taskNodes.entrySet()) {
            graph.add(node.getValue());
            for (AssetsConverter.ConvertProcess dependency : node.getKey().getDependencies()) {
                graph.addDependency(taskNodes.get(dependency), node.getValue());
            }

            // Update the count of the tasks we are supposed to run
            if (isNeedForExecuting(node.getValue())) {
                tasksToRun++;
            }
        }
    }

    private void executeTask(TaskNode node) {
        if (failure) {
            LOGGER.log(Level.INFO, "Aborting execution of task {0}!", node);
            return;
        }
        LOGGER.log(Level.INFO, "Starting task {0}!", node);

        Future task = executorService.submit(() -> {
            try {
                node.executeTask();

                LOGGER.log(Level.INFO, "Task {0} finished!", node);

                executeNextTask(node);
            } catch (Exception e) {
                failure = true;
                LOGGER.log(Level.ERROR, "Task " + node + " failed! Aborting...", e);

                executorService.shutdown();
            }
        });
        runningTasks.put(node, task);

        // See if we have now set all the tasks to the execution queue
        int tasksRunningValue = tasksRunning.incrementAndGet();
        if (tasksRunningValue == tasksToRun) {
            executorService.shutdown();
        }
    }

    private void executeNextTask(TaskNode node) {
        if (!node.hasOutgoingNodes()) {
            return;
        }

        /**
         * Get the next possible task<br>
         * Synchronize since the finished tasks can enter here simultaneously
         * and add the same tasks to the queue
         */
        synchronized (graph) {
            new TaskExecuterGraphTraverser().traverse(node);
        }
    }

    private boolean isNeedForExecuting(TaskNode node) {
        return !node.isExecuted() && !runningTasks.containsKey(node);
    }

    private boolean canExecute(TaskNode node) {
        for (TaskNode parentNode : node.getIncomingNodes()) {
            if (!parentNode.isExecuted()) {
                return false;
            }
        }

        return true;
    }

    private void sortNodesChildCountDesc(List<TaskNode> childNodes) {
        Collections.sort(childNodes, (o1, o2) -> {
            return Integer.compare(o2.getOutgoingNodes().size(), o1.getOutgoingNodes().size());
        });
    }

    /**
     * Traverses through tasks, but stops traversing if the node is not
     * completed (adjacent tasks can't be run)
     *
     * @author Toni Helenius <helenius.toni@gmail.com>
     */
    public class TaskExecuterGraphTraverser extends BreadthFirstTraverser<TaskNode> {

        public void traverse(TaskNode startNode) {
            traverse(startNode, (node) -> {
                if (isNeedForExecuting(node)) {
                    executeTask(node);
                }
                return node.isExecuted();
            });
        }

        @Override
        protected boolean isVisitChildValid(TaskNode childNode) {
            return canExecute(childNode);
        }

    }

}
