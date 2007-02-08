//
// $Id$

package com.threerings.bang.bounty.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Properties;

import com.samskivert.util.StringUtil;

import com.threerings.io.SimpleStreamableObject;
import com.threerings.util.MessageBundle;

import com.threerings.bang.data.Article;
import com.threerings.bang.data.AvatarInfo;
import com.threerings.bang.data.Badge;
import com.threerings.bang.data.Handle;
import com.threerings.bang.data.PlayerObject;
import com.threerings.bang.data.Star;
import com.threerings.bang.data.Stat;
import com.threerings.bang.data.StringSetStat;
import com.threerings.bang.util.BangUtil;

import com.threerings.bang.game.data.BangAI;
import com.threerings.bang.game.data.GameCodes;

import static com.threerings.bang.Log.log;

/**
 * Describes the configuration of a particular bounty.
 */
public class BountyConfig extends SimpleStreamableObject
    implements Comparable<BountyConfig>
{
    /** Defines the two different types of bounties. */
    public enum Type { TOWN, MOST_WANTED };

    /** Defines a reward for completing a bounty. */
    public static class Reward extends SimpleStreamableObject
    {
        /** Scrip payed when this bounty is completed. */
        public int scrip;

        /** An article of clothing given out as a reward or null (male and female). */
        public Article[] articles;

        /** A badge to be given out as a reward or null. */
        public Badge.Type badge;
    }

    /** Describes a particular opponent in a bounty game. */
    public static class Opponent extends SimpleStreamableObject
    {
        /** This opponent's name. */
        public String name;

        /** The avatar for this opponent. */
        public AvatarInfo avatar = new AvatarInfo();
    }

    /** Defines a speaker and text for a quote. */
    public static class Quote extends SimpleStreamableObject
    {
        /** The player index of the speaker of the quote. */
        public int speaker;

        /** The (translated) text of the quote. */
        public String text = "";

        /** Returns true if no required configuration is missing. */
        public boolean isValid () {
            return !StringUtil.isBlank(text);
        }
    }

    /** Defines the various bits for each bounty game. */
    public static class GameInfo extends SimpleStreamableObject
    {
        /** A string identifying this game. */
        public String ident;

        /** The (translated) name of this game. */
        public String name;

        /** Information on our opponents, if any. */
        public Opponent[] opponents;

        /** A quote shown before the bounty game. */
        public Quote preGameQuote;

        /** A quote shown if the bounty game is failed. */
        public Quote failedQuote;

        /** A quote shown if the bounty game is completed. */
        public Quote completedQuote;

        /** Logs a warning if required values are not specified. */
        public void validate (String which) {
            ArrayList<String> missing = new ArrayList<String>();
            if (StringUtil.isBlank(name)) {
                missing.add("name");
            }
            if (!preGameQuote.isValid()) {
                missing.add("pregame_quote");
            }
            if (!failedQuote.isValid()) {
                missing.add("failed_quote");
            }
            if (!completedQuote.isValid()) {
                missing.add("completed_quote");
            }
            if (missing.size() > 0) {
                log.warning("Bounty game misconfigured [bounty=" + which + ", game=" + ident +
                            ", missing=" + missing + "].");
            }
        }
    }

    /** The town in which this bounty is available. */
    public String townId;

    /** Whether this is a Town or a Most Wanted bounty. */
    public Type type;

    /** Uniquely identifies this bounty. */
    public String ident;

    /** Used to force an order on bounties with the same scrip reward. */
    public int order;

    /** The (translated) title of this bounty. Generally the Outlaw's name. */
    public String title;

    /** The (translated) description of this bounty. Shown in the Sheriff's Office. */
    public String description;

    /** The difficulty of this bounty. */
    public Star.Difficulty difficulty;

    /** Whether or not the bounty games must be played in order. */
    public boolean inOrder = true;

    /** An avatar for the outlaw. */
    public AvatarInfo outlaw = new AvatarInfo();

    /** The names of our game definition files. */
    public ArrayList<GameInfo> games = new ArrayList<GameInfo>();

    /** The reward earned by completing this bounty. */
    public Reward reward;

    /**
     * Returns the bounty with the specified identifier.
     */
    public static BountyConfig getBounty (String ident)
    {
        ensureBountiesLoaded();
        return _configs.get(ident);
    }

    /**
     * Returns a sorted list of all bounties for the specified town and type.
     */
    public static ArrayList<BountyConfig> getTownBounties (String townId, Type type)
    {
        ensureBountiesLoaded();
        ArrayList<BountyConfig> matches = new ArrayList<BountyConfig>();
        for (BountyConfig config : _configs.values()) {
            if (config.type == type && config.townId.equals(townId)) {
                matches.add(config);
            }
        }
        Collections.sort(matches);
        return matches;
    }

    /**
     * Returns true if this bounty is available to the specified player.
     */
    public boolean isAvailable (PlayerObject user)
    {
        if (user.tokens.isSupport()) {
            return true;

        } else if (type == Type.MOST_WANTED) {
            return user.holdsStar(BangUtil.getTownIndex(townId), difficulty);

        } else if (difficulty == Star.Difficulty.EASY) {
            return true;

        } else {
            Star.Difficulty level = Star.getPrevious(difficulty);
            int cleared = 0;
            StringSetStat completed = (StringSetStat)
                user.stats.get(Stat.Type.BOUNTIES_COMPLETED.name());
            if (completed != null) {
                for (String ident : completed.values()) {
                    BountyConfig config = getBounty(ident);
                    if (config != null && config.difficulty == level) {
                        cleared++;
                    }
                }
            }
            return cleared >= TOWN_CLEAR_PROGRESS;
        }
    }

    /**
     * Returns the stat set key used to identify the supplied game (which must be one of this
     * bounty's games).
     */
    public String getStatKey (String game)
    {
        return ident.substring(ident.lastIndexOf("/")+1) + "." + game;
    }

    /**
     * Returns the info record for the game with the specified ident or null.
     */
    public GameInfo getGame (String game)
    {
        for (GameInfo info : games) {
            if (info.ident.equals(game)) {
                return info;
            }
        }
        return null;
    }

    /**
     * Returns the "outlaw" targeted by this bounty as an {@link Opponent} record.
     */
    public Opponent getOutlaw ()
    {
        Opponent outop = new Opponent();
        outop.name = title;
        outop.avatar = outlaw;
        return outop;
    }

    /**
     * Returns a configured AI record for the specified opponent in this game.
     */
    public BangAI getOpponent (String game, int players, int index, BangAI oppai)
    {
        GameInfo info = getGame(game);
        if (info == null) {
            log.warning("Requested opponent for unknown game [bounty=" + ident +
                        ", game=" + game + "].");
            return oppai;
        }

        Opponent opp = info.opponents == null ? null : info.opponents[index];
        if (opp != null) {
            oppai.handle = new Handle(opp.name);
            oppai.avatar = opp.avatar;
        } else if (index == players-1) {
            oppai.handle = new Handle(title);
            oppai.avatar = outlaw;
        }

        // don't let a malformed avatarInfo sneak through
        if (oppai.avatar != null && oppai.avatar.image == null && 
                (oppai.avatar.print == null || oppai.avatar.print.length == 0)) {
            oppai.avatar = null;
            log.warning("Malformed avatar [bounty=" + ident + ", game=" + game + 
                        ", index=" + index + "].");
        }

        return oppai;
    }

    /**
     * Checks whether all of this bounty's games have been completed by the specified player.
     */
    public boolean isCompleted (PlayerObject user)
    {
        // if they have completed all the games, they're done!
        for (GameInfo game : games) {
            if (!user.stats.containsValue(
                    Stat.Type.BOUNTY_GAMES_COMPLETED, getStatKey(game.ident))) {
                return false;
            }
        }
        return true;
    }

    // from interface Comparable<BountyConfig>
    public int compareTo (BountyConfig other)
    {
        if (other.type != type) {
            return type.ordinal() - other.type.ordinal();
        }
        if (other.difficulty != difficulty) {
            return difficulty.ordinal() - other.difficulty.ordinal();
        }
        if (other.reward.scrip != reward.scrip) {
            return reward.scrip - other.reward.scrip;
        }
        if (order != other.order) {
            return order - other.order;
        }
        if (other.games.size() != games.size()) {
            return games.size() - other.games.size();
        }
        return ident.compareTo(other.ident);
    }

    /**
     * Parses and registers a bounty from its property file definition.
     */
    protected static void registerBounty (String which)
    {
        // load up the properties file for this bounty
        Properties props = BangUtil.resourceToProperties(
            "rsrc/bounties/" + which + "/bounty.properties");

        // split the which into some metadata
        String[] bits = which.split("/");
        BountyConfig config = new BountyConfig();
        config.townId = bits[0];
        config.type = Type.valueOf(bits[1].toUpperCase());
        config.ident = bits[2] + "/" + bits[3];

        // parse the various bounty properties
        config.order = BangUtil.getIntProperty(which, props, "order", 0);
        config.difficulty =
            BangUtil.getEnumProperty(which, props, "difficulty", Star.Difficulty.EASY);
        config.inOrder = BangUtil.getBooleanProperty(which, props, "in_order", config.inOrder);
        config.outlaw.print = StringUtil.parseIntArray(props.getProperty("outlaw_print", ""));
        config.outlaw.image = getImageProperty(which, props, "outlaw_image");
        config.title = props.getProperty("title", "");
        config.description = props.getProperty("descrip", "");

        for (String game : StringUtil.parseStringArray(props.getProperty("games", ""))) {
            GameInfo info = new GameInfo();
            info.ident = game;
            info.name = props.getProperty(game + ".name", "");
            info.opponents = new Opponent[GameCodes.MAX_PLAYERS];
            for (int ii = 1; ii < info.opponents.length; ii++) {
                String prefix = game + ".opponent." + ii;
                String name = props.getProperty(prefix + ".name");
                if (name != null) {
                    info.opponents[ii] = new Opponent();
                    info.opponents[ii].name =  name;
                    info.opponents[ii].avatar.print =
                        StringUtil.parseIntArray(props.getProperty(prefix + ".print", ""));
                    info.opponents[ii].avatar.image =
                        getImageProperty(which, props, prefix + ".image");
                }
            }
            info.preGameQuote = parseQuote(which, props, game + ".pregame");
            info.failedQuote = parseQuote(which, props, game + ".failed");
            info.completedQuote = parseQuote(which, props, game + ".completed");
            info.validate(which);
            config.games.add(info);
        }

        config.reward = new Reward();
        config.reward.scrip = BangUtil.getIntProperty(which, props, "reward_scrip", 0);

        Article male = parseArticle(which, props.getProperty("reward_article_male", ""));
        Article female = parseArticle(which, props.getProperty("reward_article_female", ""));
        if (male != null || female != null) {
            if (male == null || female == null) {
                log.warning("Missing article for geneder [which=" + which +
                            ", male=" + male + ", female=" + female + "].");
            } else {
                config.reward.articles = new Article[] { male, female };
            }
        }

        String badge = props.getProperty("reward_badge", "");
        if (badge.length() > 0) {
            try {
                config.reward.badge = Enum.valueOf(Badge.Type.class, badge);
            } catch (Exception e) {
                log.warning("Invalid badge reward specified in bounty [which=" + which +
                            ", badge=" + badge + "].");
            }
        }

        // finally map it
        BountyConfig collide = _configs.put(config.ident, config);
        if (collide != null) {
            log.warning("Bounty name collision [which=" + which + ", old=" + collide + "].");
        }

        log.fine("Registered " + config + ".");
    }

    protected static Quote parseQuote (String which, Properties props, String prefix)
    {
        Quote quote = new Quote();
        quote.text = props.getProperty(prefix + "_quote");
        quote.speaker = BangUtil.getIntProperty(which, props, prefix + "_speaker", 1);
        return quote;
    }

    protected static Article parseArticle (String which, String article)
    {
        if (article == null || article.length() == 0) {
            return null;
        }
        String[] bits = article.split(":");
        if (bits.length != 3) {
            log.warning("Invalid article reward specified in bounty [which=" + which +
                        ", article=" + article + "].");
            return null;
        }
        return new Article(0, bits[0], bits[1], StringUtil.parseIntArray(bits[2]));
    }

    protected static String getImageProperty (String which, Properties props, String key)
    {
        String image = props.getProperty(key, (String)null);
        return StringUtil.isBlank(image) ? null : "bounties/" + which + "/" + image;
    }

    protected static void ensureBountiesLoaded ()
    {
        if (_configs.size() == 0) {
            String[] configs = BangUtil.townResourceToStrings("rsrc/bounties/TOWN/bounties.txt");
            for (int ii = 0; ii < configs.length; ii++) {
                registerBounty(configs[ii]);
            }
        }
    }

    /** We cache all loaded bounty configs. */
    protected static HashMap<String,BountyConfig> _configs = new HashMap<String,BountyConfig>();

    /** The number of Town Bounties that must be cleared to progress to the next level. */
    protected static final int TOWN_CLEAR_PROGRESS = 4;
}
