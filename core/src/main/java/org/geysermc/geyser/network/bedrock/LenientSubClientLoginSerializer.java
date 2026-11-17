/*
 * Copyright (c) 2019-2026 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.network.bedrock;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketSerializer;
import org.cloudburstmc.protocol.bedrock.codec.v818.serializer.SubClientLoginSerializer_v818;
import org.cloudburstmc.protocol.bedrock.packet.SubClientLoginPacket;

/**
 * Decodes a splitscreen login without kicking the whole connection.
 * A second PlayStation controller often has no Xbox chain, and the stock
 * serializer throws on that, which Geyser treats as an illegal packet.
 */
public class LenientSubClientLoginSerializer implements BedrockPacketSerializer<SubClientLoginPacket> {
	/** Shared instance installed over the codec's SubClientLogin serializer. */
	public static final LenientSubClientLoginSerializer INSTANCE = new LenientSubClientLoginSerializer();

	private LenientSubClientLoginSerializer() {
	}

	@Override
	public void serialize(ByteBuf buffer, BedrockCodecHelper helper, SubClientLoginPacket packet) {
		SubClientLoginSerializer_v818.INSTANCE.serialize(buffer, helper, packet);
	}

	/**
	 * Reads the login the same way the current protocol does. A guest controller
	 * that fails that read still produces a packet, with the identity left empty
	 * so the session can assign one.
	 */
	@Override
	public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, SubClientLoginPacket packet) {
		try {
			SubClientLoginSerializer_v818.INSTANCE.deserialize(buffer, helper, packet);
		} catch (RuntimeException ignored) {
			buffer.readerIndex(buffer.writerIndex());
			packet.setAuthPayload(null);
			packet.setClientJwt("");
		}
	}
}
