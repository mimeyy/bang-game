//
// $Id$

package com.threerings.bang.avatar.client;

import com.threerings.presents.client.Client;
import com.threerings.presents.client.InvocationService;

/**
 * Provides avatar invocation services that are available anywhere, not just
 * when in the Barber Shop.
 */
public interface AvatarService extends InvocationService
{
    /**
     * Selects the specified look as the player's "current" look.
     *
     * @param name the name of the look to be selected.
     */
    public void selectLook (Client client, String name);
}
