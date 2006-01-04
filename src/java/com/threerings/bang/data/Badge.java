//
// $Id$

package com.threerings.bang.data;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;

import com.samskivert.util.ArrayIntSet;
import com.samskivert.util.HashIntMap;

import com.threerings.bang.util.BangUtil;

import static com.threerings.bang.Log.log;

/**
 * Represents a badge earned by the player by meeting some specified
 * criterion.
 */
public class Badge extends Item
{
    /** Defines the various badge types. */
    public static enum Type
    {
        // temporary names: these will be replaced with more descriptive monikers
        DAILY_HIGH_SCORER, // daily high scorer
        WEEKLY_HIGH_SCORER, // weekly
        MONTHLY_HIGH_SCORER, // monthly

        // games played badges
        GAMES_PLAYED_1 {
            public boolean qualifies (PlayerObject user) {
                return user.stats.getIntStat(Stat.Type.GAMES_PLAYED) >= 5;
            }
        },
        GAMES_PLAYED_2 {
            public boolean qualifies (PlayerObject user) {
                return user.stats.getIntStat(Stat.Type.GAMES_PLAYED) >= 50;
            }
        },
        GAMES_PLAYED_3 {
            public boolean qualifies (PlayerObject user) {
                return user.stats.getIntStat(Stat.Type.GAMES_PLAYED) >= 500;
            }
        },
        GAMES_PLAYED_4 {
            public boolean qualifies (PlayerObject user) {
                return user.stats.getIntStat(Stat.Type.GAMES_PLAYED) >= 2000;
            }
        },
        GAMES_PLAYED_5 {
            public boolean qualifies (PlayerObject user) {
                return user.stats.getIntStat(Stat.Type.GAMES_PLAYED) >= 5000;
            }
        },
        GAMES_PLAYED_6 {
            public boolean qualifies (PlayerObject user) {
                return user.stats.getIntStat(Stat.Type.GAMES_PLAYED) >= 10000;
            }
        },
        GAMES_PLAYED_7 {
            public boolean qualifies (PlayerObject user) {
                return user.stats.getIntStat(Stat.Type.GAMES_PLAYED) >= 50000;
            }
        },

        // units killed badges
        UNITS_KILLED_1 {
            public boolean qualifies (PlayerObject user) {
                return user.stats.getIntStat(Stat.Type.UNITS_KILLED) >= 50;
            }
        },
        UNITS_KILLED_2 {
            public boolean qualifies (PlayerObject user) {
                return user.stats.getIntStat(Stat.Type.UNITS_KILLED) >= 500;
            }
        },
        UNITS_KILLED_3 {
            public boolean qualifies (PlayerObject user) {
                return user.stats.getIntStat(Stat.Type.UNITS_KILLED) >= 5000;
            }
        },
        UNITS_KILLED_4 {
            public boolean qualifies (PlayerObject user) {
                return user.stats.getIntStat(Stat.Type.UNITS_KILLED) >= 20000;
            }
        },
        UNITS_KILLED_5 {
            public boolean qualifies (PlayerObject user) {
                return user.stats.getIntStat(Stat.Type.UNITS_KILLED) >= 50000;
            }
        },

        // cattle rustled badges
        CATTLE_RUSTLED_1 {
            public boolean qualifies (PlayerObject user) {
                return user.stats.getIntStat(Stat.Type.CATTLE_RUSTLED) >= 10;
            }
        },
        CATTLE_RUSTLED_2 {
            public boolean qualifies (PlayerObject user) {
                return user.stats.getIntStat(Stat.Type.CATTLE_RUSTLED) >= 100;
            }
        },
        CATTLE_RUSTLED_3 {
            public boolean qualifies (PlayerObject user) {
                return user.stats.getIntStat(Stat.Type.CATTLE_RUSTLED) >= 1000;
            }
        },
        CATTLE_RUSTLED_4 {
            public boolean qualifies (PlayerObject user) {
                return user.stats.getIntStat(Stat.Type.CATTLE_RUSTLED) >= 10000;
            }
        },

        // nuggets collected badges
        NUGGETS_COLLECTED_1 {
            public boolean qualifies (PlayerObject user) {
                return user.stats.getIntStat(Stat.Type.NUGS_COLLECTED) >= 10;
            }
        },
        NUGGETS_COLLECTED_2 {
            public boolean qualifies (PlayerObject user) {
                return user.stats.getIntStat(Stat.Type.NUGS_COLLECTED) >= 100;
            }
        },
        NUGGETS_COLLECTED_3 {
            public boolean qualifies (PlayerObject user) {
                return user.stats.getIntStat(Stat.Type.NUGS_COLLECTED) >= 1000;
            }
        },
        NUGGETS_COLLECTED_4 {
            public boolean qualifies (PlayerObject user) {
                return user.stats.getIntStat(Stat.Type.NUGS_COLLECTED) >= 10000;
            }
        },

        // badges for using (owning) bigshots
        FRONTIER_BIGSHOTS_USED, // all Frontier Town bigshots owned (used)
        INDIAN_BIGSHOTS_USED, // all Indian Village bigshots owned (used)
        BOOM_BIGSHOTS_USED, // all Boom Town bigshots owned (used)
        GHOST_BIGSHOTS_USED, // all Ghost Town bigshots owned (used)
        GOLD_BIGSHOTS_USED, // all City of Gold bigshots owned (used)

        // badges for using special units
        FRONTIER_SPECIALS_USED, // all Frontier Town special units used
        INDIAN_SPECIALS_USED, // all Indian Village special units used
        BOOM_SPECIALS_USED, // all Boom Town special units used
        GHOST_SPECIALS_USED, // all Ghost Town special units used
        GOLD_SPECIALS_USED, // all City of Gold special units used

        UNUSED;

        /** Returns a new blank stat instance of the specified type. */
        public Badge newBadge ()
        {
            return new Badge(code());
        }

        /** Overridden by each badge type to indicate whether the supplied
         * user qualifies for this badge. */
        public boolean qualifies (PlayerObject user) {
            return false;
        }

        /** Returns the translation key used by this badge. */
        public String key () {
            return "m.badge_" + Integer.toHexString(_code);
        }

        /** Returns the unique code for this badge type, which is a function of
         * its name. */
        public int code () {
            return _code;
        }

        Type () {
            // compute our unique code
            StringBuffer codestr = new StringBuffer();
            _code = BangUtil.crc32(name());

            if (_codeToType.containsKey(_code)) {
                log.warning("Badge type collision! " + this + " and " +
                            _codeToType.get(_code) + " both map to '" +
                            _code + "'.");
            } else {
                _codeToType.put(_code, this);
//                 System.out.println(key() + " = " + this);
            }
        }

        protected int _code;
    };

    /**
     * Determines whether this player qualifies for any new badges and
     * adds those for which they qualify to the supplied list.
     */
    public static void checkBadges (PlayerObject user, ArrayList<Badge> badges)
    {
        // first enumerate the badges they already hold
        _badgeCodes.clear();
        for (Iterator iter = user.inventory.iterator(); iter.hasNext(); ) {
            Object item = iter.next();
            if (item instanceof Badge) {
                _badgeCodes.add(((Badge)item).getCode());
            }
        }

        // now check each type in turn for qualification
        for (Type type : EnumSet.allOf(Type.class)) {
            if (_badgeCodes.contains(type.code())) {
                continue;
            }
            if (!type.qualifies(user)) {
                continue;
            }
            Badge badge = type.newBadge();
            badge.setOwnerId(user.playerId);
            badges.add(badge);
        }
    }

    /** Creates a blank instance for serialization. */
    public Badge ()
    {
    }

    /**
     * Maps a {@link Type}'s code code back to a {@link Type} instance.
     */
    public static Type getType (int code)
    {
        return (Type)_codeToType.get(code);
    }

    /**
     * Returns the type of this badge.
     */
    public Type getType ()
    {
        return getType(_code);
    }

    /**
     * Returns the integer code to which this badge's type maps.
     */
    public int getCode ()
    {
        return _code;
    }

    /**
     * Creates a new, unowned badge with the specified type code.
     */
    protected Badge (int code)
    {
        _code = code;
    }

    @Override // documentation inherited
    protected void toString (StringBuffer buf)
    {
        super.toString(buf);
        buf.append(", type=").append(getType());
    }

    /** The unique code for the type of this badge. */
    protected int _code;

    /** The table mapping stat codes to enumerated types. */
    protected static HashIntMap _codeToType = new HashIntMap();

    /** Trigger the loading of the enum when we load this class. */
    protected static Type _trigger = Type.UNUSED;

    /** Used by {@link #checkBadges}. */
    protected static ArrayIntSet _badgeCodes = new ArrayIntSet();
}
