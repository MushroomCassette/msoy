//
// $Id$

package com.threerings.msoy.game.data {

import com.threerings.io.ObjectInputStream;

import com.threerings.presents.dobj.DSet;

import com.whirled.game.data.WhirledGameObject;

import com.threerings.msoy.party.data.PartyLeader;
import com.threerings.msoy.party.data.PartyPlaceObject;
import com.threerings.msoy.party.data.PartySummary;

/**
 * Extends Whirled game stuff with party awareness.
 */
public class MsoyGameObject extends WhirledGameObject
    implements PartyPlaceObject
{
    // AUTO-GENERATED: FIELDS START
    /** The field name of the <code>parties</code> field. */
    public static const PARTIES :String = "parties";

    /** The field name of the <code>partyLeaders</code> field. */
    public static const PARTY_LEADERS :String = "partyLeaders";
    // AUTO-GENERATED: FIELDS END

    // force linkage
    MsoyGameOccupantInfo;

    /** Information on the parties presently in this game. */
    public var parties :DSet; /* of */ PartySummary; // force linkage

    /** Current party leaders. */
    public var partyLeaders :DSet; /* of */ PartyLeader;

    // from PartyPlaceObject
    public function getParties () :DSet
    {
        return parties;
    }

    // from PartyPlaceObject
    public function getOccupants () :DSet
    {
        return occupantInfo;
    }

    override protected function readDefaultFields (ins :ObjectInputStream) :void
    {
        super.readDefaultFields(ins);

        parties = DSet(ins.readObject());
        partyLeaders = DSet(ins.readObject());
    }
}
}
