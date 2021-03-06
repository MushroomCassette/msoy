//
// $Id$

package com.threerings.msoy.badge.data;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import com.threerings.stats.Log;
import com.threerings.stats.data.StatSet;

import com.threerings.msoy.badge.data.all.Badge;
import com.threerings.msoy.badge.data.all.BadgeCodes;
import com.threerings.msoy.badge.data.all.EarnedBadge;
import com.threerings.msoy.badge.gwt.StampCategory;
import com.threerings.msoy.data.StatType;

/** Defines the various badge types. */
@com.threerings.util.ActionScript(omit=true)
public enum BadgeType
{
    // Note: If you add a new BadgeType, VERSION should be incremented.

    // social badges
    FRIENDLY(StampCategory.SOCIAL, StatType.FRIENDS_MADE, new Level[] {
        new Level(6, 250),
        new Level(10, 500),
        new Level(50, 1000),
        new Level(100, 1500),
        new Level(200, 2000),
        new Level(500, 10000)
    }),

    // TODO: IRONMAN has been punted on for now, hopefully short-term
//    IRONMAN(StampCategory.SOCIAL, StatType.CONSEC_DAILY_LOGINS, new Level[] {
//        new Level(2, 1000),
//        new Level(4, 2000),
//        new Level(10, 3000),
//        new Level(25, 4000),
//        new Level(90, 5000),
//        new Level(180, 6000)
//        }) {
//        @Override protected Collection<BadgeType> getUnlockRequirements () {
//            return Collections.singleton(FRIENDLY);
//        }
//    },

    MAGNET(StampCategory.SOCIAL, StatType.INVITES_ACCEPTED, new Level[] {
        new Level(1, 500),
        new Level(5, 1000),
        new Level(10, 3000),
        new Level(20, 4000),
        new Level(50, 5000),
        new Level(100, 10000)
        }) {
        @Override protected Collection<BadgeType> getUnlockRequirements () {
            return Collections.singleton(FRIENDLY);
        }
    },

    FIXTURE(StampCategory.SOCIAL, StatType.MINUTES_ACTIVE, new Level[] {
        new Level(3 * 60, 250),
        new Level(24 * 60, 1000),
        new Level(48 * 60, 1500),
        new Level(96 * 60, 4000),
        new Level(200 * 60, 5000),
        new Level(500 * 60, 10000)
        }) {
        @Override public String getRequiredUnitsString (int levelNumber) {
            Level level = getLevel(levelNumber);
            // the real unit is minutes, but we tell the player hours
            return level == null ? null : "" + (level.requiredUnits / 60);
        }

        @Override public boolean progressValid (int levelNumber) {
            // always show progress
            return true;
        }

        @Override protected Collection<BadgeType> getUnlockRequirements () {
            return Collections.singleton(FRIENDLY);
        }
    },

    EXPLORER(StampCategory.SOCIAL, StatType.ROOMS_TOURED, new Level[] {
        new Level(25, 250),
        new Level(50, 500),
        new Level(100, 1000),
        new Level(200, 2000),
        new Level(500, 3000),
        new Level(2000, 10000)
    }),

    // game badges
    GAMER(StampCategory.GAME, StatType.GAME_SESSIONS, new Level[] {
        new Level(2, 250),
        new Level(10, 500),
        new Level(25, 1000),
        new Level(100, 2000),
        new Level(500, 3000),
        new Level(2000, 10000)
    }),

    CONTENDER(StampCategory.GAME, StatType.MP_GAMES_WON, new Level[] {
        new Level(1, 250),
        new Level(5, 500),
        new Level(10, 1000),
        new Level(25, 2000),
        new Level(50, 3000),
        new Level(100, 10000)
        }) {
        @Override protected Collection<BadgeType> getUnlockRequirements () {
            return Collections.singleton(GAMER);
        }
    },

    COLLECTOR(StampCategory.GAME, StatType.TROPHIES_EARNED, new Level[] {
        new Level(5, 250),
        new Level(15, 500),
        new Level(30, 1000),
        new Level(75, 2000),
        new Level(150, 3000),
        new Level(300, 10000)
        }) {
        @Override protected Collection<BadgeType> getUnlockRequirements () {
            return Collections.singleton(GAMER);
        }
    },

    // creation badges
    CHARACTER_DESIGNER(StampCategory.CREATION, StatType.AVATARS_CREATED, new Level[] {
        new Level(1, 250),
        new Level(2, 1000),
        new Level(3, 2000)
        }) {
        @Override public boolean progressValid (int levelUnits) {
            // never show a progress meter
            return false;
        }

        @Override protected Collection<BadgeType> getUnlockRequirements () {
            return Collections.singleton(FURNITURE_BUILDER);
        }
    },

    FURNITURE_BUILDER(StampCategory.CREATION, StatType.FURNITURE_CREATED, new Level[] {
        new Level(1, 250),
        new Level(2, 500),
        new Level(3, 1000)
        }) {
        @Override public boolean progressValid (int levelUnits) {
            // never show a progress meter
            return false;
        }
    },

    LANDSCAPE_PAINTER(StampCategory.CREATION, StatType.BACKDROPS_CREATED, new Level[] {
        new Level(1, 250),
        new Level(2, 400),
        new Level(3, 800)
        }) {
        @Override public boolean progressValid (int levelUnits) {
            // never show a progress meter
            return false;
        }

        @Override protected Collection<BadgeType> getUnlockRequirements () {
            return Collections.singleton(FURNITURE_BUILDER);
        }
    },

    // now Merchant in the UI
    PROFESSIONAL(StampCategory.CREATION, StatType.COINS_EARNED_SELLING, new Level[] {
        new Level(10000, 1000),
        new Level(100000, 2000),
        new Level(500000, 3000),
        new Level(1000000, 4000),
        new Level(2000000, 5000),
        new Level(5000000, 10000)
        }) {
        // PROFESSIONAL is unlocked once you have at least one other CREATION badge
        @Override public boolean isUnlocked (Collection<EarnedBadge> badges) {
            return Iterables.any(badges, new Predicate<EarnedBadge>() {
                public boolean apply (EarnedBadge badge) {
                    return getType(badge.badgeCode).getCategory() == StampCategory.CREATION;
                }
            });
        }

        @Override public String getRequiredUnitsString (int levelNumber) {
            Level level = getLevel(levelNumber);
            if (level == null) {
                return null;
            }

            // these get big, so lets abbreviate them.
            if (level.requiredUnits >= 1000000) {
                return (level.requiredUnits / 1000000) + "M";
            } else if (level.requiredUnits >= 1000) {
                return (level.requiredUnits / 1000) + "k";
            } else {
                return "" + level.requiredUnits;
            }
        }

        @Override public boolean progressValid (int levelUnits) {
            // always show a progress meter
            return true;
        }
    },

    ARTISAN(StampCategory.CREATION, StatType.SOLID_4_STAR_RATINGS, new Level[] {
        new Level(1, 1000),
        new Level(5, 2000),
        new Level(10, 3000),
        new Level(15, 4000),
        new Level(20, 5000),
        new Level(25, 10000)
        }) {
        // ARTISAN is unlocked once you have at least one other CREATION badge
        @Override public boolean isUnlocked (Collection<EarnedBadge> badges) {
            return Iterables.any(badges, new Predicate<EarnedBadge>() {
                public boolean apply (EarnedBadge badge) {
                    return getType(badge.badgeCode).getCategory() == StampCategory.CREATION;
                }
            });
        }

        @Override protected int getAcquiredUnits (StatSet stats) {
            return stats.getSetStatSize(StatType.SOLID_4_STAR_RATINGS);
        }
    },

    // shopping badges
    SHOPPER(StampCategory.SHOPPING, StatType.COINS_SPENT, new Level[] {
        new Level(1, 250),
        new Level(5000, 500),
        new Level(15000, 1000),
        new Level(50000, 2000),
        new Level(100000, 3000),
        new Level(200000, 10000),
        }) {
        @Override public String getRequiredUnitsString (int levelNumber) {
            if (levelNumber < 1) {
                return super.getRequiredUnitsString(levelNumber);
            }

            Level level = getLevel(levelNumber);
            if (level == null) {
                return null;
            }

            // these get big, so lets abbreviate them.
            return (level.requiredUnits / 1000) + "k";
        }
    },

    JUDGE(StampCategory.SHOPPING, StatType.ITEMS_RATED, new Level[] {
        new Level(1, 100),
        new Level(5, 200),
        new Level(25, 3000),
        new Level(100, 4000),
        new Level(500, 5000),
        new Level(2000, 10000)
        }) {
        @Override protected Collection<BadgeType> getUnlockRequirements () {
            return Collections.singleton(SHOPPER);
        }
    },

    OUTSPOKEN(StampCategory.SHOPPING, StatType.ITEM_COMMENTS, new Level[] {
        new Level(1, 250),
        new Level(5, 500),
        new Level(25, 1000),
        new Level(100, 4000),
        new Level(500, 5000),
        new Level(2000, 10000)
        }) {
        @Override protected Collection<BadgeType> getUnlockRequirements () {
            return Collections.singleton(SHOPPER);
        }
    },
    ; // end of BadgeTypes. If you add a new type, VERSION should be incremented.

    /** The version number of the set of badges. If you add a new badge, this needs to be
     * incremented. */
    public static final short VERSION = 2;

    /** Function to get the badgeCode out of a Badge */
    public static final Function<Badge, Integer> BADGE_TO_CODE = new Function<Badge, Integer>() {
        public Integer apply (Badge badge) {
            return badge.badgeCode;
        }
    };

    /** Encapsulates a stat requirement and coin reward for a single badge level. */
    public static class Level
    {
        public int requiredUnits;
        public int coinValue;

        public Level (int requiredUnits, int coinValue) {
            this.requiredUnits = requiredUnits;
            this.coinValue = coinValue;
        }
    }

    /**
     * A main method so that this class can be run on its own for Badge code discovery.
     * A human readable summary is output unless one of the following arguments is given:
     * <ul><li>-gen: outputs java code for the {@link BadgeCodes} class, declaring each badge
     * code as a named constant for use in gwt without pulling in the whole of BadgeType.</li>
     * <li>-genas: outputs the actionscript code for com.threerings.msoy.badge.data.all.BadgeCodes
     * so that badges can be identified by name in the flash client</li><li>-genswitch: outputs
     * a switch statement containing a case for each constant in BadgeCodes</li></ul>
     */
    public static void main (String[] args)
    {
        if (args.length > 0 && args[0].equals("-gen")) {
            System.out.println("package com.threerings.msoy.badge.data.all;");
            System.out.println("");
            System.out.println("// AUTO GENERATED from " + BadgeType.class.getName());
            System.out.println("public class BadgeCodes");
            System.out.println("{");
            for (BadgeType type : values()) {
                String code = Integer.toHexString(type.getCode());
                System.out.println(
                    "    public static final int " + type.name() + " = 0x" + code + ";");
            }
            System.out.println("}");

        } else if (args.length > 0 && args[0].equals("-genas")) {
            System.out.println("package com.threerings.msoy.badge.data.all {");
            System.out.println("");
            System.out.println("// AUTO GENERATED from " + BadgeType.class.getName());
            System.out.println("public class BadgeCodes");
            System.out.println("{");
            for (BadgeType type : values()) {
                String code = Integer.toHexString(type.getCode());
                System.out.println(
                    "    public static const " + type.name() + " :uint = 0x" + code + ";");
            }
            System.out.println("}\n}");

        } else if (args.length > 0 && args[0].equals("-genpy")) {
            System.out.println("");
            System.out.println("## AUTO GENERATED from " + BadgeType.class.getName());
            System.out.println("codes = {");
            for (BadgeType type : values()) {
                System.out.println(
                    "    " + type.getCode() + ": '" + type.name() + "',");
            }
            System.out.println("}");

        } else if (args.length > 0 && args[0].equals("-genswitch")) {
            System.out.print("switch (badgeCode) {");
            for (BadgeType type : values()) {
                System.out.println();
                System.out.println("case BadgeCodes." + type.name() + ":");
                System.out.println("    break;");
            }
            System.out.println("}");

        } else {
            // dump all of the known badge types and their code
            System.out.println("  Hex    -   Integer   - Badge\n--------------------");
            for (Map.Entry<Integer, BadgeType> entry : _codeToType.entrySet()) {
                System.out.println(Integer.toHexString(entry.getKey()) + " - " + entry.getKey() +
                    " - " + entry.getValue());
            }
        }
    }

    /**
     * Maps a {@link BadgeType}'s code back to a {@link BadgeType} instance.
     */
    public static BadgeType getType (int code)
    {
        return _codeToType.get(code);
    }

    /**
     * Pulls the levelUnits string out of the type that maps to the given code
     */
    public static String getRequiredUnitsString (int badgeCode, int level)
    {
        BadgeType type = getType(badgeCode);
        return type == null ? null : type.getRequiredUnitsString(level);
    }

    /**
     * Returns a set of the badges that depend on the given stat.  When that stat is changed, the
     * party responsible for the change should call this method and find out what badges potentially
     * need updating.
     */
    public static Set<BadgeType> getDependantBadges (StatType stat)
    {
        Set<BadgeType> depends = (stat == null) ? null : _statDependencies.get(stat.code());
        return (depends == null) ? Collections.<BadgeType>emptySet() : depends;
    }

    /**
     * Certain badges are only unlocked once one or more other badges are earned.
     *
     * @return true if this badge has been unlocked, given the specified collection of EarnedBadges.
     */
    public boolean isUnlocked (Collection<EarnedBadge> badges)
    {
        Collection<BadgeType> reqs = getUnlockRequirements();
        return reqs.isEmpty() || // either there are no requirements, or we satisfy them all
            Sets.newHashSet(Iterables.transform(badges, BADGE_TO_TYPE)).containsAll(reqs);
    }

    /** Constructs a new BadgeType. */
    BadgeType (StampCategory category, StatType relevantStat, Level[] levels)
    {
        _category = category;
        _relevantStat = relevantStat;
        _levels = levels;

        // compute the code hash
        CRC32 crc = new CRC32();
        crc.update(name().getBytes());
        _code = (int) crc.getValue();

        // ensure the badge has at least one level
        if (_levels == null || _levels.length == 0) {
            _levels = new Level[] { new Level(1, 0) };
        }
    }

    /**
     * @return the number of levels this badge has.
     */
    public int getNumLevels ()
    {
        return _levels.length;
    }

    /**
     * @return the level data for the specified badge level, or null if the level is out of range.
     */
    public Level getLevel (int level)
    {
        return (level >= 0 && level < _levels.length ? _levels[level] : null);
    }

    /**
     * Convenience method to get the requiredUnits for the given level number.  This function
     * will return null if the level is not found.
     */
    public String getRequiredUnitsString (int levelNumber)
    {
        Level level = getLevel(levelNumber);
        return level == null ? null : "" + level.requiredUnits;
    }

    /**
     * Conveninence method to get the coin reward for the given level number.  This function
     * will return 0 if the level is not found.
     */
    public int getCoinValue (int levelNumber)
    {
        Level level = getLevel(levelNumber);
        return level == null ? 0 : level.coinValue;
    }

    /**
     * Returns true if progress is a valid metric for this level of this badge.
     */
    public boolean progressValid (int levelNumber)
    {
        // most badges don't want to show a progress meter for the first level, but do for the
        // rest so let's make that the default
        return levelNumber != 0;
    }

    /**
     * Returns the progress that the specified user has made on this badge.
     */
    public BadgeProgress getProgress (StatSet stats)
    {
        int highestLevel = -1;
        int requiredUnits = 0;
        int acquiredUnits = getAcquiredUnits(stats);
        if (_levels != null) {
            for (Level level : _levels) {
                if (acquiredUnits >= level.requiredUnits) {
                    highestLevel++;
                } else {
                    requiredUnits = level.requiredUnits;
                    break;
                }
            }
        }

        return new BadgeProgress(highestLevel, requiredUnits, acquiredUnits);
    }

    /**
     * @return the unique code for this badge type, which is a function of its name.
     */
    public final int getCode()
    {
        return _code;
    }

    /**
     * @return the relevant StatType associated with this badge, or null if the badge doesn't have
     * one. The badge system uses this information to update badges when their associated stats
     * are updated.
     */
    public StatType getRelevantStat ()
    {
        return _relevantStat;
    }

    /**
     * @return the Category this badge falls under.
     */
    public StampCategory getCategory ()
    {
        return _category;
    }

    /**
     * Overridden by badge types to indicate how many units of the stat that this badge tracks
     * (games played, friends made, etc) the user has acquired.
     */
    protected int getAcquiredUnits (StatSet stats)
    {
        return stats.getIntStat(_relevantStat);
    }

    /**
     * Optionally overridden by badge types to indicate that players must earn a particular set
     * of badges before this badge becomes unlocked.
     */
    protected Collection<BadgeType> getUnlockRequirements ()
    {
        return Collections.emptyList();
    }

    protected static void mapStatDependencies (BadgeType type)
    {
        if (_statDependencies == null) {
            _statDependencies = Maps.newHashMap();
        }

        StatType stat = type.getRelevantStat();
        if (stat != null) {
            int code = stat.code();
            Set<BadgeType> dependantTypes = _statDependencies.get(code);
            if (dependantTypes == null) {
                _statDependencies.put(code, dependantTypes = Sets.newHashSet());
            }
            dependantTypes.add(type);
        }
    }

    protected int _code;
    protected StampCategory _category;
    protected StatType _relevantStat;
    protected Level[] _levels;

    /** The table mapping stat codes to enumerated types. */
    protected static Map<Integer, BadgeType> _codeToType = Maps.newHashMap();

    /** The mapping of stats to the badges that depend on them. */
    protected static Map<Integer, Set<BadgeType>> _statDependencies;

    /**
     * Create the hash<->BadgeType mapping for each BadgeType.
     * This is done in a static block because it's an error for an enum
     * to access its static members in its constructor.
     */
    static
    {
        for (BadgeType type : values()) {
            // map it, see if it collides
            BadgeType collideType = _codeToType.put(type.getCode(), type);
            if (null != collideType) {
                Log.log.warning("Badge type collision!", "type1", type, "type2", collideType,
                    "code", type.getCode());
            }
            // set up any dependencies
            mapStatDependencies(type);
        }
    }

    /** Helper function. */
    protected static final Function<Badge, BadgeType> BADGE_TO_TYPE =
        new Function<Badge, BadgeType>() {
        public BadgeType apply (Badge badge) {
            return BadgeType.getType(badge.badgeCode);
        }
    };
}
