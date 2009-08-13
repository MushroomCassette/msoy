//
// $Id$

package com.threerings.msoy.facebook.gwt;

import java.util.List;

import com.google.gwt.user.client.rpc.IsSerializable;
import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.RemoteServiceRelativePath;

import com.threerings.msoy.web.gwt.ServiceException;

/**
 * Provides services for munging Facebook and Whirled data for display.
 */
@RemoteServiceRelativePath(value=FacebookService.REL_PATH)
public interface FacebookService extends RemoteService
{
    public static final String ENTRY_POINT = "/fbpage";
    public static final String REL_PATH = "../../.." + FacebookService.ENTRY_POINT;

    /**
     * Genders for the purposes of a facebook invite.
     */
    public static enum Gender { MALE, FEMALE, HIDDEN };

    /**
     * Provides data for the invitaion request form.
     */
    public static class InviteInfo
        implements IsSerializable
    {
        /** The sending user's name. */
        public String username;

        /** The sending user's gender, for pronoun selection. */
        public Gender gender;

        /** The game being invited to, only used when requesting a info for a game. */
        public String gameName;

        /** The friends to exclude (ones that are already using the application). */
        public List<Long> excludeIds;
    }

    /**
     * Data required for publishing a simple feed story.
     */
    public static class StoryFields
        implements IsSerializable
    {
        /** The template bundle to use, normally a randomly selected variant. */
        public FacebookTemplateCard template;

        /** The thumbnail to use in the story. */
        public String thumbnailURL;

        /** The game name. */
        public String name;

        /** The game description, normally used as the body of the story. */
        public String description;
    }

    /**
     * Gets a random template variant from those with a matching code. If none match, null is
     * returned.
     */
    FacebookTemplateCard getTemplate (String code)
        throws ServiceException;

    /**
     * Notes that the user published a trophy to their feed (or at least viewed the publish
     * dialog).
     */
    void trophyPublished (int gameId, String ident)
        throws ServiceException;

    /**
     * Retrieves the list of friends and their associated info for the currently logged in user.
     */
    List<FacebookFriendInfo> getAppFriendsInfo ()
        throws ServiceException;

    /**
     * Retrieves the list of friends who have played the given game and their associated info for
     * the currently logged in user.
     * TODO: this is not yet being used because mochi games are totally unintegrated (no ratings or
     * trophies).
     */
    List<FacebookFriendInfo> getGameFriendsInfo (int gameId)
        throws ServiceException;

    /**
     * Retrieves the information for sending an invite to the given game, or the application if
     * if game is null.
     */
    InviteInfo getInviteInfo (FacebookGame game)
        throws ServiceException;

    /**
     * Sends a challenge notification to all friends of the logged in user, optionally limiting
     * to only those friends that use the application. Returns data for publishing a challenge
     * feed story, or null if the data could not be loaded.
     */
    StoryFields sendChallengeNotification (FacebookGame game, boolean appOnly)
        throws ServiceException;

    /**
     * Returns data for publishing a challenge feed story. Throws an exception if the data could
     * not be loaded.
     */
    StoryFields getStoryFields (FacebookGame game)
        throws ServiceException;
}
