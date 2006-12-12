//
// $Id$

package com.threerings.bang.game.server.scenario;

import com.threerings.bang.data.BonusConfig;
import com.threerings.bang.data.Stat;

import com.threerings.bang.game.data.BangObject;
import com.threerings.bang.game.data.effect.FoolsNuggetEffect;
import com.threerings.bang.game.data.effect.HoldEffect;
import com.threerings.bang.game.data.effect.NuggetEffect;
import com.threerings.bang.game.data.piece.Bonus;
import com.threerings.bang.game.data.piece.Counter;
import com.threerings.bang.game.data.piece.Unit;
import com.threerings.bang.game.data.scenario.NuggetScenarioInfo;

/**
 * Handles the deposit and extraction of nuggets from claims/steam tanks.
 */
public class NuggetDelegate extends CounterDelegate
{
    public NuggetDelegate (boolean allowClaimWithdrawal, int startingCount)
    {
        _allowClaimWithdrawal = allowClaimWithdrawal;
        _startingCount = startingCount;
    }

    @Override // documentation inherited
    public void roundDidEnd (BangObject bangobj)
    {
        // note each players' nugget related stats
        for (Counter counter : _counters) {
            bangobj.stats[counter.owner].incrementStat(Stat.Type.NUGGETS_CLAIMED, counter.count);
            bangobj.stats[counter.owner].maxStat(Stat.Type.MOST_NUGGETS, counter.count);
        }
    }

    @Override // documentation inherited
    protected int startingCount ()
    {
        return _startingCount;
    }

    @Override // documentation inherited
    protected int pointsPerCounter ()
    {
        return NuggetScenarioInfo.POINTS_PER_NUGGET;
    }

    @Override // documentation inherited
    protected void checkAdjustedCounter (BangObject bangobj, Unit unit)
    {
        if (_counters == null || _counters.size() == 0) {
            return;
        }

        // if this unit landed next to one of the counters, do some stuff
        Counter counter = null;
        for (Counter c : _counters) {
            if (c.getDistance(unit) <= 1) {
                if (!_allowClaimWithdrawal && c.owner != unit.owner) {
                    continue;
                }
                counter = c;
                break;
            }
        }
        if (counter == null) {
            return;
        }

        // deposit or withdraw a nugget as appropriate
        NuggetEffect effect = null;
        if (counter.owner == unit.owner && NuggetEffect.NUGGET_BONUS.equals(unit.holding)) {
            effect = new NuggetEffect();
            effect.dropping = true;

        } else if (counter.owner == unit.owner &&
                   FoolsNuggetEffect.FOOLS_NUGGET_BONUS.equals(unit.holding)) {
            effect = new FoolsNuggetEffect();
            effect.dropping = true;

        } else if (_allowClaimWithdrawal && counter.owner != unit.owner && counter.count > 0 &&
                   unit.canActivateBonus(bangobj, _nuggetBonus) &&
                   !NuggetEffect.NUGGET_BONUS.equals(unit.holding)) {
            if (unit.holding != null) {
                HoldEffect dropEffect = HoldEffect.dropBonus(bangobj, unit, -1, unit.holding);
                _bangmgr.deployEffect(unit.owner, dropEffect);
            }
            effect = new NuggetEffect();
            effect.dropping = false;
        }

        if (effect != null) {
            effect.init(unit);
            effect.claimId = counter.pieceId;
            _bangmgr.deployEffect(unit.owner, effect);
        }
    }

    protected boolean _allowClaimWithdrawal;
    protected int _startingCount;

    /** A prototype nugget bonus used to ensure that pieces can hold it. */
    protected Bonus _nuggetBonus =
        Bonus.createBonus(BonusConfig.getConfig(NuggetEffect.NUGGET_BONUS));
}
