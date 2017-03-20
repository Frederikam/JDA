package net.dv8tion.jda.core.managers.impl;

import com.sun.jna.Platform;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.audio.AudioConnection;
import net.dv8tion.jda.core.audio.AudioReceiveHandler;
import net.dv8tion.jda.core.audio.AudioSendHandler;
import net.dv8tion.jda.core.audio.hooks.ConnectionListener;
import net.dv8tion.jda.core.audio.hooks.ConnectionStatus;
import net.dv8tion.jda.core.audio.hooks.ListenerProxy;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.managers.AudioManager;
import net.dv8tion.jda.core.utils.NativeUtil;

import java.io.IOException;

public abstract class AbstractAudioManager implements AudioManager
{
    //These values are set at the bottom of this file.
    public static boolean AUDIO_SUPPORTED;
    public static String OPUS_LIB_NAME;

    protected static boolean initialized = false;

    public final Object CONNECTION_LOCK = new Object();

    protected AudioConnection audioConnection = null;
    protected VoiceChannel queuedAudioConnection = null;

    protected AudioSendHandler sendHandler;
    protected AudioReceiveHandler receiveHandler;
    protected ListenerProxy connectionListener = new ListenerProxy();
    protected long queueTimeout = 100;
    protected boolean shouldReconnect = true;

    protected boolean selfMuted = false;
    protected boolean selfDeafened = false;

    protected long timeout = DEFAULT_CONNECTION_TIMEOUT;

    @Override
    public abstract void openAudioConnection(VoiceChannel channel);

    @Override
    public void closeAudioConnection()
    {
        closeAudioConnection(ConnectionStatus.NOT_CONNECTED);
    }

    public abstract void closeAudioConnection(ConnectionStatus reason);

    @Override
    public abstract JDA getJDA();

    @Override
    public abstract Guild getGuild();

    @Override
    public boolean isAttemptingToConnect()
    {
        return queuedAudioConnection != null;
    }

    @Override
    public VoiceChannel getQueuedAudioConnection()
    {
        return queuedAudioConnection;
    }

    @Override
    public VoiceChannel getConnectedChannel()
    {
        return audioConnection == null ? null : audioConnection.getChannel();
    }

    @Override
    public boolean isConnected()
    {
        return audioConnection != null;
    }

    @Override
    public void setConnectTimeout(long timeout)
    {
        this.timeout = timeout;
    }

    @Override
    public long getConnectTimeout()
    {
        return timeout;
    }

    @Override
    public void setSendingHandler(AudioSendHandler handler)
    {
        sendHandler = handler;
        if (audioConnection != null)
            audioConnection.setSendingHandler(handler);
    }

    @Override
    public AudioSendHandler getSendingHandler()
    {
        return sendHandler;
    }

    @Override
    public void setReceivingHandler(AudioReceiveHandler handler)
    {
        receiveHandler = handler;
        if (audioConnection != null)
            audioConnection.setReceivingHandler(handler);
    }

    @Override
    public AudioReceiveHandler getReceiveHandler()
    {
        return receiveHandler;
    }

    @Override
    public void setConnectionListener(ConnectionListener listener)
    {
        this.connectionListener.setListener(listener);
    }

    @Override
    public ConnectionListener getConnectionListener()
    {
        return connectionListener.getListener();
    }

    @Override
    public ConnectionStatus getConnectionStatus()
    {
        if (audioConnection != null)
            return audioConnection.getWebSocket().getConnectionStatus();
        else
            return ConnectionStatus.NOT_CONNECTED;
    }

    @Override
    public void setAutoReconnect(boolean shouldReconnect)
    {
        this.shouldReconnect = shouldReconnect;
        if (audioConnection != null)
            audioConnection.getWebSocket().setAutoReconnect(shouldReconnect);
    }

    @Override
    public boolean isAutoReconnect()
    {
        return shouldReconnect;
    }

    @Override
    public void setSelfMuted(boolean muted)
    {
        if (selfMuted != muted)
        {
            this.selfMuted = muted;
            updateVoiceState();
        }
    }

    @Override
    public boolean isSelfMuted()
    {
        return selfMuted;
    }

    @Override
    public void setSelfDeafened(boolean deafened)
    {
        if (selfDeafened != deafened)
        {
            this.selfDeafened = deafened;
            updateVoiceState();
        }

    }

    @Override
    public boolean isSelfDeafened()
    {
        return selfDeafened;
    }

    public ConnectionListener getListenerProxy()
    {
        return connectionListener;
    }

    public void setAudioConnection(AudioConnection audioConnection)
    {
        this.audioConnection = audioConnection;
        if (audioConnection == null)
            return;

        this.queuedAudioConnection = null;
        audioConnection.setSendingHandler(sendHandler);
        audioConnection.setReceivingHandler(receiveHandler);
        audioConnection.setQueueTimeout(queueTimeout);
        audioConnection.ready(timeout);
    }

    public void prepareForRegionChange()
    {
        VoiceChannel queuedChannel = audioConnection.getChannel();
        closeAudioConnection(ConnectionStatus.AUDIO_REGION_CHANGE);
        this.queuedAudioConnection = queuedChannel;
    }

    public void setQueuedAudioConnection(VoiceChannel channel)
    {
        queuedAudioConnection = channel;
    }

    public void setConnectedChannel(VoiceChannel channel)
    {
        if (audioConnection != null)
            audioConnection.setChannel(channel);
    }

    public void setQueueTimeout(long queueTimeout)
    {
        this.queueTimeout = queueTimeout;
        if (audioConnection != null)
            audioConnection.setQueueTimeout(queueTimeout);
    }

    protected abstract void updateVoiceState();

    //Load the Opus library.
    static synchronized boolean init()
    {
        if(initialized)
            return AUDIO_SUPPORTED;
        initialized = true;
        String nativesRoot  = null;
        try
        {
            //The libraries that this is referencing are available in the src/main/resources/opus/ folder.
            //Of course, when JDA is compiled that just becomes /opus/
            nativesRoot = "/natives/" + Platform.RESOURCE_PREFIX + "/%s";
            if (nativesRoot.contains("darwin")) //Mac
                nativesRoot += ".dylib";
            else if (nativesRoot.contains("win"))
                nativesRoot += ".dll";
            else if (nativesRoot.contains("linux"))
                nativesRoot += ".so";
            else
                throw new UnsupportedOperationException();

            NativeUtil.loadLibraryFromJar(String.format(nativesRoot, "libopus"));
        }
        catch (Throwable e)
        {
            if (e instanceof UnsupportedOperationException)
                LOG.fatal("Sorry, JDA's audio system doesn't support this system.\n" +
                        "Supported Systems: Windows(x86, x64), Mac(x86, x64) and Linux(x86, x64)\n" +
                        "Operating system: " + Platform.RESOURCE_PREFIX);
            else if (e instanceof IOException)
            {
                LOG.fatal("There was an IO Exception when setting up the temp files for audio.");
                LOG.log(e);
            }
            else if (e instanceof UnsatisfiedLinkError)
            {
                LOG.fatal("JDA encountered a problem when attempting to load the Native libraries. Contact a DEV.");
                LOG.log(e);
            }
            else
            {
                LOG.fatal("An unknown error occurred while attempting to setup JDA's audio system!");
                LOG.log(e);
            }

            nativesRoot = null;
        }
        finally
        {
            OPUS_LIB_NAME = nativesRoot != null ? String.format(nativesRoot, "libopus") : null;
            AUDIO_SUPPORTED = nativesRoot != null;

            if (AUDIO_SUPPORTED)
                LOG.info("Audio System successfully setup!");
            else
                LOG.info("Audio System encountered problems while loading, thus, is disabled.");
            return AUDIO_SUPPORTED;
        }

    }
}