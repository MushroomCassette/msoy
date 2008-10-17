//
// $Id$

package com.threerings.msoy.game.server;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.samskivert.util.HashIntMap;
import com.samskivert.util.IntMap;

import com.threerings.presents.annotation.EventThread;

import com.threerings.msoy.world.server.WorldWatcherManager;

import static com.threerings.msoy.Log.log;

/**
 * The game-server-side API for {@link WorldWatcherManager}; a registry of watched members whose
 * movements from scene to scene anywhere in the Whirled (i.e. cross server) are relayed to
 * the associated {@link Observer} objects.
 *
 * The sole client of this service is currently AVRGameManager, which needs to keep track of
 * what rooms its various current players are in.
 */
@Singleton @EventThread
public class GameWatcherManager
{
    /**
     * Interface for notifying the AVRGameManager of the whereabouts of a member.
     */
    public static interface Observer
    {
        /**
         * Notifies that a member has moved to a new scene and/or logged on.
         */
        void memberMoved (int memberId, int sceneId, String hostname, int port);
        
        /**
         * Notifies that a member has logged off.
         */
        void memberLoggedOff (int memberId);
    }

    /**
     * Subscribe to notification of this member's scene-to-scene movements on the world servers.
     */
    public void addWatch (int memberId, Observer observer)
    {
        Observer old = _observers.put(memberId, observer);
        if (old != null) {
            log.warning("Displaced existing watcher", "memberId", "observer", old);
        }
        _worldClient.addWatch(memberId);
    }

    /**
     * Clear an existing movement watch on the given member.
     */
    public void clearWatch (int memberId)
    {
        Observer old = _observers.remove(memberId);
        if (old == null) {
            log.warning("Attempt to clear non-existent watch", "memberId", memberId);
        }
        _worldClient.clearWatch(memberId);
    }

    /**
     * Notification of member movement, from {@link WorldServerClient}.
     */
    public void memberMoved (int memberId, int sceneId, String hostname, int port)
    {
        Observer observer = _observers.get(memberId);
        log.info(
            "Member moved", "memberId", memberId, "sceneId", sceneId, "hostname", hostname,
            "port", port, "observer", observer);
        if (observer != null) {
            observer.memberMoved(memberId, sceneId, hostname, port);
        }
    }

    /**
     * Notification of member logging off, from {@link WorldServerClient}.
     */
    public void memberLoggedOff (int memberId)
    {
        Observer observer = _observers.get(memberId);
        log.info("Member logged off", "memberId", memberId);
        if (observer != null) {
            observer.memberLoggedOff(memberId);
        }
    }

    /** A map of members to {@link Observer} objects to notify of each member's movements. */
    protected IntMap<Observer> _observers = new HashIntMap<Observer>();

    // our dependencies
    @Inject protected WorldServerClient _worldClient;
}
