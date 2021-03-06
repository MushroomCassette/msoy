//
// $Id$

package com.threerings.msoy.group.gwt;

import com.threerings.msoy.web.gwt.ServiceCodes;

/**
 * Codes and constants relating to the group services.
 */
public interface GroupCodes extends ServiceCodes
{
    /** An error code reported by the group services. */
    public static final String E_GROUP_NAME_IN_USE = "e.group_name_in_use";

    /** An error code to represent an attempt to create a duplicate medal. */
    public static final String E_GROUP_MEDAL_NAME_IN_USE = "e.group_medal_name_in_use";

    /** An error code to represent an attempt to award a medal to a player that already has said
     * medal */
    public static final String E_GROUP_MEMBER_HAS_MEDAL = "e.group_member_has_medal";

    /** An error code to indicate that a group may not be made exclusive while it has tags. */
    public static final String E_GROUP_TAGS_ON_EXCLUSIVE = "e.group_tags_on_exclusive";

    /** An error code reported when a group is not found or inaccessible. */
    public static final String E_INVALID_GROUP = "e.invalid_group";
}
