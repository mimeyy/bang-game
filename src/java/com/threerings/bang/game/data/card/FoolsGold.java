//
// $Id$

package com.threerings.bang.game.data.card;

import com.threerings.bang.data.BangCodes;
import com.threerings.bang.data.BonusConfig;
import com.threerings.bang.game.data.BangObject;
import com.threerings.bang.game.data.piece.Bonus;
import com.threerings.bang.game.data.piece.Piece;
import com.threerings.bang.game.data.scenario.NuggetScenarioInfo;

/**
 * Places a "fool's nugget" on the board that looks like a gold nugget, but
 * isn't one.
 */
public class FoolsGold extends AddPieceCard
{
    @Override // documentation inherited
    public String getType ()
    {
        return "fools_gold";
    }
    
    @Override // documentation inherited
    public boolean isPlayable (BangObject bangobj)
    {
        return super.isPlayable(bangobj) &&
            (bangobj.scenario instanceof NuggetScenarioInfo);
    }

    @Override // documentation inherited
    public String getTownId ()
    {
        return BangCodes.FRONTIER_TOWN;
    }

    @Override // documentation inherited
    public int getWeight ()
    {
        return 35;
    }

    @Override // documentation inherited
    public int getScripCost ()
    {
        return 150;
    }

    @Override // documentation inherited
    public int getCoinCost ()
    {
        return 0;
    }
    
    // documentation inherited
    protected Piece createPiece ()
    {
        return Bonus.createBonus(
            BonusConfig.getConfig("frontier_town/fools_nugget"));
    }
}
