/*
 * This file is part of ViaProxy - https://github.com/RaphiMC/ViaProxy
 * Copyright (C) 2021-2024 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.raphimc.viaproxy.protocoltranslator.providers;

import com.viaversion.viabackwards.protocol.protocol1_20_3to1_20_5.storage.CookieStorage;
import com.viaversion.viaversion.api.connection.UserConnection;
import io.netty.channel.Channel;
import net.raphimc.viabedrock.protocol.providers.TransferProvider;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;
import net.raphimc.viaproxy.proxy.util.CloseAndReturn;
import net.raphimc.viaproxy.proxy.util.TransferDataHolder;

import java.net.InetSocketAddress;

public class ViaProxyTransferProvider extends TransferProvider implements com.viaversion.viabackwards.protocol.protocol1_20_3to1_20_5.provider.TransferProvider {

    @Override
    public void connectToServer(UserConnection user, InetSocketAddress newAddress) {
        final Channel channel = ProxyConnection.fromChannel(user.getChannel()).getC2P();
        TransferDataHolder.addTempRedirect(channel, newAddress);
        if (user.has(CookieStorage.class)) {
            TransferDataHolder.addCookieStorage(channel, user.get(CookieStorage.class));
        }
        try {
            ProxyConnection.fromUserConnection(user).kickClient("§aThe server transferred you to another server §7(§e" + newAddress.getHostName() + ":" + newAddress.getPort() + "§7)§a. Please reconnect to ViaProxy.");
        } catch (CloseAndReturn ignored) {
        }
    }

    @Override
    public void connectToServer(UserConnection user, String host, int port) {
        this.connectToServer(user, new InetSocketAddress(host, port));
    }

}
