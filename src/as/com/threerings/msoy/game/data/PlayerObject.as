//
// $Id$

package com.threerings.msoy.game.data {

import com.whirled.game.client.PropertySpaceHelper;
import com.whirled.game.data.PropertySpaceMarshaller;
import com.whirled.game.data.PropertySpaceObject;
import com.whirled.game.data.WhirledPlayerObject;

import com.threerings.io.ObjectInputStream;

import com.threerings.util.Name;

import com.threerings.crowd.data.TokenRing;

import com.threerings.orth.data.MediaDesc;

import com.threerings.msoy.data.MsoyTokenRing;
import com.threerings.msoy.data.all.MemberMailUtil;
import com.threerings.msoy.data.all.VisitorInfo;
import com.threerings.msoy.data.all.VizMemberName;

/**
 * Contains information on a player logged on to an MSOY Game server.
 */
public class PlayerObject extends WhirledPlayerObject
    implements PropertySpaceObject
{
    GameAuthName // filled into username

    // AUTO-GENERATED: FIELDS START
    /** The field name of the <code>memberName</code> field. */
    public static const MEMBER_NAME :String = "memberName";

    /** The field name of the <code>tokens</code> field. */
    public static const TOKENS :String = "tokens";

    /** The field name of the <code>photo</code> field. */
    public static const PHOTO :String = "photo";

    /** The field name of the <code>game</code> field. */
    public static const GAME :String = "game";

    /** The field name of the <code>visitorInfo</code> field. */
    public static const VISITOR_INFO :String = "visitorInfo";
    // AUTO-GENERATED: FIELDS END

    /** The name and id information for this user. */
    public var memberName :VizMemberName;

    /** The tokens defining the access controls for this user. */
    public var tokens :MsoyTokenRing;

    /* The game summary for the game that the player is lobbying for or currently playing. */
    public var game :GameSummary;

    /** How many coins we've got jangling around on our person. */
    public var coins :int;

    /** The number of bars the member has currently in their account. */
    public var bars :int;

    /** Player's tracking information. */
    public var visitorInfo :VisitorInfo;

    /** Service for setting properties. */
    public var propertyService :PropertySpaceMarshaller;

    // from BodyObject
    override public function getTokens () :TokenRing
    {
        return tokens;
    }

    // from BodyObject
    override public function getVisibleName () :Name
    {
        return memberName;
    }

    /**
     * Returns this member's unique id.
     */
    public function getMemberId () :int
    {
        return memberName.getId();
    }

    /**
     * Returns true if this user is a permaguest.
     */
    public function isPermaguest () :Boolean
    {
        return MemberMailUtil.isPermaguest(username.toString());
    }

    /**
     * Get the media to use as our headshot.
     */
    public function getHeadShotMedia () :MediaDesc
    {
        return memberName.getPhoto();
    }

    // from PropertySpaceObject
    public function getUserProps () :Object
    {
        return _props;
    }

    // from PropertySpaceObject
    public function getPropService () :PropertySpaceMarshaller
    {
        return propertyService;
    }

    // from BodyObject
    override public function readObject (ins :ObjectInputStream) :void
    {
        super.readObject(ins);

        // first read any regular bits
        readDefaultFields(ins);

        // then user properties
        PropertySpaceHelper.readProperties(this, ins);
    }

    /**
     * Reads the fields written by the default serializer for this instance.
     */
    protected function readDefaultFields (ins :ObjectInputStream) :void
    {
        memberName = VizMemberName(ins.readObject());
        tokens = MsoyTokenRing(ins.readObject());
        game = GameSummary(ins.readObject());
        coins = ins.readInt();
        bars = ins.readInt();
        visitorInfo = VisitorInfo(ins.readObject());
        propertyService = PropertySpaceMarshaller(ins.readObject());
    }

    /** The raw properties set by the game. */
    protected var _props :Object = new Object();
}
}
