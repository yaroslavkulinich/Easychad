/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package ml.easychad.lax.messenger;

public class NetworkMessage {
    public TLRPC.TL_protoMessage protoMessage;
    public Object rawRequest;
    public long requestId;
}
