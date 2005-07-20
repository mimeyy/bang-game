//
// $Id$

package com.threerings.bang.game.data.piece;

import java.awt.Point;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.logging.Level;

import com.samskivert.util.ArrayIntSet;
import com.threerings.io.ObjectInputStream;
import com.threerings.io.ObjectOutputStream;
import com.threerings.util.RandomUtil;

import com.threerings.bang.game.client.sprite.BonusSprite;
import com.threerings.bang.game.client.sprite.PieceSprite;

import com.threerings.bang.data.BonusConfig;
import com.threerings.bang.game.data.BangObject;
import com.threerings.bang.game.data.effect.Effect;

import static com.threerings.bang.Log.log;

/**
 * Represents an exciting bonus waiting to be picked up by a player on the
 * board. Bonuses may generate full-blown effects or just influence the
 * piece that picked them up.
 */
public class Bonus extends Piece
{
// //         MISSILE(50, // base weight
// //                 0.0, // damage affinity
// //                 0.5, // many pieces affinity
// //                 1.0, // few pieces affinity
// //                 1.0, // low power affinity
// //                 0.5, // early-game affinity
// //                 1.0) // late-game affinity
// //         {
// //             public Effect affect (Piece piece) {
// //                 return new GrantCardEffect(piece.owner, new Missile());
// //             }
// //         },

// //         AREA_REPAIR(50, // base weight
// //                     1.0, // damage affinity
// //                     0.5, // many pieces affinity
// //                     0.0, // few pieces affinity
// //                     1.0, // low power affinity
// //                     0.0, // early-game affinity
// //                     0.5) // late-game affinity
// //         {
// //             public Effect affect (Piece piece) {
// //                 return new GrantCardEffect(piece.owner, new AreaRepair());
// //             }
// //         },

// //         DUST_DEVIL(50, // base weight
// //                    0.5, // damage affinity
// //                    0.0, // many pieces affinity
// //                    0.5, // few pieces affinity
// //                    0.0, // low power affinity
// //                    -0.25, // early-game affinity
// //                    0.4) // late-game affinity
// //         {
// //             public boolean isValid (BangObject bangobj) {
// //                 // make sure there are some dead pieces on the board
// //                 return bangobj.countDeadPieces() > 1;
// //             }

// //             public Effect affect (Piece piece) {
// //                 return new GrantCardEffect(piece.owner, new DustDevil());
// //             }
// //         },

//         SAINT_ELMO(10, // base weight
//                    0.5, // damage affinity
//                    -0.5, // many pieces affinity
//                    0.6, // few pieces affinity
//                    0.0, // low power affinity
//                    0.0, // early-game affinity
//                    0.5) // late-game affinity
//         {
//             public boolean isValid (BangObject bangobj) {
//                 // make sure there are some dead pieces on the board
//                 return bangobj.countDeadPieces() > 2;
//             }

//             public Effect affect (Piece piece) {
//                 return new SaintElmosEffect(piece.owner);
//             }
//         },

    /**
     * Takes the various circumstances into effect and selects a bonus to
     * be placed on the board at the specified position which can be
     * reached on this same turn by the specfied set of players.
     */
    public static Bonus selectBonus (
        BangObject bangobj, Point bspot, ArrayIntSet reachers)
    {
        BonusConfig[] configs = BonusConfig.getTownBonuses(bangobj.townId);
        int[] weights = _weights.get(bangobj.townId);
        if (weights == null) {
            // create a scratch array of the appropriate size
            _weights.put(bangobj.townId, weights = new int[configs.length]);
        }            

        // if no one can reach the spot, base our calculations on all the
        // players instead
        if (reachers == null) {
            reachers = new ArrayIntSet();
            for (int ii = 0; ii < bangobj.players.length; ii++) {
                reachers.add(ii);
            }
        }

        // now compute some information on the reachers
        double avgpow = bangobj.getAveragePower(reachers);
        int avgdam = bangobj.getAveragePieceDamage(reachers);
        int avgpieces = bangobj.getAveragePieceCount(reachers);

        // now compute weightings for each of our bonuses
        StringBuffer buf = new StringBuffer();
        Arrays.fill(weights, 0);
        for (int ii = 0; ii < configs.length; ii++) {
            BonusConfig config = configs[ii];
// TODO: instantiate a prototype of the custom bonus class for each type?
//             if (!config.isValid(bangobj)) {
//                 continue;
//             }
            weights[ii] = config.getWeight(bangobj, avgpow, avgdam, avgpieces);
            weights[ii] = Math.max(weights[ii], 0);
            // record data for logging
            if (buf.length() > 0) {
                buf.append(", ");
            }
            buf.append(config).append(" ").append(weights[ii]);
        }

        log.info("Selecting bonus [turn=" + bangobj.tick +
                 ", avgpow=" + avgpow + ", avgdam=" + avgdam +
                 ", avgpc=" + avgpieces + ", reachers=" + reachers +
                 ", weights=(" + buf + ")].");

        // and select one at random
        return createBonus(configs[RandomUtil.getWeightedIndex(weights)]);
    }

    /**
     * Creates a bonus of the specified type.
     */
    public static Bonus createBonus (BonusConfig config)
    {
        try {
            Bonus bonus;
            if (config.bonusClass != null) {
                bonus = (Bonus)Class.forName(config.bonusClass).newInstance();
            } else {
                bonus = new Bonus();
            }
            bonus.init(config);
            return bonus;

        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to instantiate custom bonus class " +
                    "[class=" + config.bonusClass + "].", e);
            return null;
        }
    }

    /**
     * Configures this bonus instance with the good stuff.
     */
    public void init (BonusConfig config)
    {
        _config = config;
    }

    /**
     * Returns the configuration for this bonus.
     */
    public BonusConfig getConfig ()
    {
        return _config;
    }

    /**
     * Called when a piece has landed on this bonus and is activating it,
     * this should return an object indicating the effect that the bonus
     * has on this piece or the entire board. Those effects will be
     * processed at the end of the tick.
     */
    public Effect affect (Piece piece)
    {
        try {
            Effect effect = (Effect)Class.forName(
                _config.effectClass).newInstance();
            effect.init(piece);
            return effect;
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to instantiate effect class " +
                    "[class=" + _config.effectClass + "].", e);
            return null;
        }
    }

    @Override // documentation inherited
    public boolean preventsOverlap (Piece lapper)
    {
        return false;
    }

    @Override // documentation inherited
    public PieceSprite createSprite ()
    {
        return new BonusSprite(_config.type);
    }

    @Override // documentation inherited
    public String info ()
    {
        return super.info() + " t:" + _config.type;
    }

    /** Configures the instance after unserialization. */
    public void readObject (ObjectInputStream in)
        throws IOException, ClassNotFoundException
    {
        in.defaultReadObject();
        init(BonusConfig.getConfig(in.readUTF()));
    }

    /** Writes some custom information for this piece. */
    public void writeObject (ObjectOutputStream out)
        throws IOException
    {
        out.defaultWriteObject();
        out.writeUTF(_config.type);
    }

    /** The configuration for the bonus we represent. */
    protected transient BonusConfig _config;

    /** Used when selecting a random bonus. */
    protected static HashMap<String,int[]> _weights =
        new HashMap<String,int[]>();
}
