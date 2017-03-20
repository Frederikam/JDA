/*
 *     Copyright 2015-2017 Austin Keener & Michael Ritter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.dv8tion.jda.core.managers.impl;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.audio.hooks.ConnectionStatus;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.entities.impl.JDAImpl;
import net.dv8tion.jda.core.exceptions.GuildUnavailableException;
import net.dv8tion.jda.core.exceptions.PermissionException;
import org.apache.http.util.Args;
import org.json.JSONObject;

public class AudioManagerImpl extends AbstractAudioManager
{
    protected final JDAImpl api;
    protected final Guild guild;

    public AudioManagerImpl(Guild guild)
    {
        this.guild = guild;
        this.api = (JDAImpl) guild.getJDA();
        init(); //Just to make sure that the audio libs have been initialized.
    }

    @Override
    public void openAudioConnection(VoiceChannel channel)
    {
        Args.notNull(channel, "Provided VoiceChannel");

        if (!AUDIO_SUPPORTED)
            throw new UnsupportedOperationException("Sorry! Audio is disabled due to an internal JDA error! Contact Dev!");
        if (!guild.equals(channel.getGuild()))
            throw new IllegalArgumentException("The provided VoiceChannel is not a part of the Guild that this AudioManager handles." +
                    "Please provide a VoiceChannel from the proper Guild");
        if (!guild.isAvailable())
            throw new GuildUnavailableException("Cannot open an Audio Connection with an unavailable guild. " +
                    "Please wait until this Guild is available to open a connection.");
        final Member self = guild.getSelfMember();
        if (!self.hasPermission(channel, Permission.VOICE_CONNECT))
            throw new PermissionException(Permission.VOICE_CONNECT);
        final int userLimit = channel.getUserLimit(); // userLimit is 0 if no limit is set!
        if (!self.hasPermission(channel, Permission.MANAGE_CHANNEL) && userLimit > 0 && userLimit <= channel.getMembers().size())
            throw new PermissionException(Permission.MANAGE_CHANNEL,
                    "Unable to connect to VoiceChannel due to userlimit! Requires permission MANAGE_CHANNEL to bypass");

        if (audioConnection == null)
        {
            //Start establishing connection, joining provided channel
            queuedAudioConnection = channel;
            api.getClient().queueAudioConnect(channel);
        }
        else
        {
            //Connection is already established, move to specified channel

            //If we are already connected to this VoiceChannel, then do nothing.
            if (channel.equals(audioConnection.getChannel()))
                return;

            api.getClient().queueAudioConnect(channel);
            audioConnection.setChannel(channel);
        }
    }

    @Override
    public void closeAudioConnection()
    {
        closeAudioConnection(ConnectionStatus.NOT_CONNECTED);
    }

    @Override
    public void closeAudioConnection(ConnectionStatus reason)
    {
        synchronized (CONNECTION_LOCK)
        {
            api.getClient().getQueuedAudioConnectionMap().remove(guild.getId());
            this.queuedAudioConnection = null;
            if (audioConnection == null)
                return;
            this.audioConnection.close(reason);
            this.audioConnection = null;
        }
    }

    @Override
    public JDA getJDA()
    {
        return api;
    }

    @Override
    public Guild getGuild()
    {
        return guild;
    }

    @Override
    protected void updateVoiceState()
    {
        if (isConnected() || isAttemptingToConnect())
        {
            VoiceChannel channel = isConnected() ? getConnectedChannel() : getQueuedAudioConnection();

            //This is technically equivalent to an audio open/move packet.
            JSONObject voiceStateChange = new JSONObject()
                    .put("op", 4)
                    .put("d", new JSONObject()
                            .put("guild_id", guild.getId())
                            .put("channel_id", channel.getId())
                            .put("self_mute", isSelfMuted())
                            .put("self_deaf", isSelfDeafened())
                    );
            api.getClient().send(voiceStateChange.toString());
        }
    }
}
