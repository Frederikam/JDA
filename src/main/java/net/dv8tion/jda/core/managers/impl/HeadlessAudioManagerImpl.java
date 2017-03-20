package net.dv8tion.jda.core.managers.impl;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.audio.hooks.ConnectionStatus;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.exceptions.GuildUnavailableException;
import net.dv8tion.jda.core.exceptions.PermissionException;
import org.apache.http.util.Args;

public class HeadlessAudioManagerImpl extends AbstractAudioManager
{
    private final String token;
    private final String vcId;

    public HeadlessAudioManagerImpl(String token, String vcId) {
        this.token = token;
        this.vcId = guildId;
    }

    @Override
    public void openAudioConnection(VoiceChannel channel) {
        Args.notNull(channel, "Provided VoiceChannel");

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
    public void closeAudioConnection(ConnectionStatus reason) {

    }

    @Override
    public JDA getJDA() {
        return null;
    }

    @Override
    public Guild getGuild() {
        return null;
    }

    @Override
    protected void updateVoiceState() {

    }
}
