package toniarts.openkeeper.game.state;

import com.jme3.system.Timer;

/**
 * A standard JME3 application that extends SimpleApplication or
 * Application tries as hard as it can to keep in sync with
 * user-time. If a ball is rolling at 1 game-mile per game-hour in the
 * game, and you wait for one user-hour as measured by the clock on
 * your wall, then the ball should have traveled exactly one
 * game-mile. In order to keep sync with the real world, the game
 * throttles its physics engine and graphics display. If the
 * computations involved in running the game are too intense, then the
 * game will first skip frames, then sacrifice physics accuracy. If
 * there are particularly demanding computations, then you may only
 * get 1 fps, and the ball may tunnel through the floor or obstacles
 * due to inaccurate physics simulation, but after the end of one
 * user-hour, that ball will have traveled one game-mile.
 *
 * When we're recording video or audio, we don't care if the game-time
 * syncs with user-time, but instead whether the time in the recorded
 * video (video-time) syncs with user-time. To continue the analogy,
 * if we recorded the ball rolling at 1 game-mile per game-hour and
 * watched the video later, we would want to see 30 fps video of the
 * ball rolling at 1 video-mile per user-hour. It doesn't matter how
 * much user-time it took to simulate that hour of game-time to make
 * the high-quality recording.  If an Application uses this IsoTimer
 * instead of the normal one, we can be sure that every call to
 * simpleUpdate, for example, corresponds to exactly (1 / fps) seconds
 * of game-time. This let's us record perfect video and audio even on
 * a slow computer.
 *
 * @author Robert McIntyre
 *
 */

final class IsoTimer extends Timer {

    private float framerate;
    private int ticks;
	
    public IsoTimer(float framerate){
	this.framerate = framerate;
	this.ticks = 0;
    }
	
    public long getTime() {
	return (long) (this.ticks / this.framerate);
    }
	
    public long getResolution() {
	return 1000000000L;
    }
	
    public float getFrameRate() {
	return this.framerate;
    }
	
    public float getTimePerFrame() {
	return (float) (1.0f / this.framerate);
    }

    public void update() {this.ticks++;}
	
    public void reset() {this.ticks = 0;}

}