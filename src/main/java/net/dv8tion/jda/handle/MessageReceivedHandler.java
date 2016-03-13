/**
 *    Copyright 2015-2016 Austin Keener & Michael Ritter
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
package net.dv8tion.jda.handle;

import net.dv8tion.jda.entities.Message;
import net.dv8tion.jda.entities.impl.JDAImpl;
import net.dv8tion.jda.events.BotInviteReceivedEvent;
import net.dv8tion.jda.events.InviteReceivedEvent;
import net.dv8tion.jda.events.message.MessageReceivedEvent;
import net.dv8tion.jda.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.utils.InviteUtil;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageReceivedHandler extends SocketHandler
{
    private static final Pattern invitePattern = Pattern.compile("\\bhttps://discord.gg/([a-zA-Z0-9-]+)\\b");
    public static final Pattern authPattern = Pattern.compile("\\bhttps://(?:www\\.)?discordapp\\.com/oauth2/authorize\\?(.+)\\b");

    public MessageReceivedHandler(JDAImpl api, int responseNumber)
    {
        super(api, responseNumber);
    }

    @Override
    public void handle(JSONObject content)
    {
        Message message = new EntityBuilder(api).createMessage(content);
        if (!message.isPrivate())
        {
            api.getEventManager().handle(
                    new GuildMessageReceivedEvent(
                            api, responseNumber,
                            message, api.getChannelMap().get(message.getChannelId())));
        }
        else
        {
            api.getEventManager().handle(
                    new PrivateMessageReceivedEvent(
                            api, responseNumber,
                            message, api.getPmChannelMap().get(message.getChannelId())));
        }
        //Combo event
        api.getEventManager().handle(
                new MessageReceivedEvent(
                        api, responseNumber,
                        message));

        //searching for invites
        Matcher matcher = invitePattern.matcher(message.getContent());
        while (matcher.find())
        {
            InviteUtil.Invite invite = InviteUtil.resolve(matcher.group(1));
            if (invite != null)
            {
                api.getEventManager().handle(
                        new InviteReceivedEvent(
                                api, responseNumber,
                                message,invite));
            }
        }

        //searching for bot-invites
        matcher = authPattern.matcher(message.getContent());
        while (matcher.find())
        {
            Map<String, String> argMap = new HashMap<>();
            String[] urlArgs = matcher.group(1).split("&");
            Arrays.stream(urlArgs).filter(a -> a.contains("=")).forEach(a -> {
                String[] split = a.split("=");
                argMap.put(split[0], split[1]);
            });

            if (argMap.containsKey("client_id") && argMap.containsKey("scope") && argMap.get("scope").equals("bot"))
            {
                api.getEventManager().handle(
                        new BotInviteReceivedEvent(api, responseNumber, message, argMap));
            }
        }
    }
}
