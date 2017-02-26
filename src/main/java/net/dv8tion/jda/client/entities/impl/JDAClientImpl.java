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

package net.dv8tion.jda.client.entities.impl;

import gnu.trove.map.TLongObjectMap;
import net.dv8tion.jda.client.JDAClient;
import net.dv8tion.jda.client.entities.*;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.EntityBuilder;
import net.dv8tion.jda.core.entities.Invite;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.entities.impl.JDAImpl;
import net.dv8tion.jda.core.requests.Request;
import net.dv8tion.jda.core.requests.Response;
import net.dv8tion.jda.core.requests.RestAction;
import net.dv8tion.jda.core.requests.Route;
import net.dv8tion.jda.core.utils.MiscUtil;
import org.apache.http.util.Args;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class JDAClientImpl implements JDAClient
{
    protected final JDAImpl api;
    protected final TLongObjectMap<Group> groups = MiscUtil.newLongMap();
    protected final TLongObjectMap<Relationship> relationships = MiscUtil.newLongMap();
    protected final TLongObjectMap<CallUser> callUsers = MiscUtil.newLongMap();
    protected UserSettingsImpl userSettings;

    public JDAClientImpl(JDAImpl api)
    {
        this.api = api;
        this.userSettings = new UserSettingsImpl(api);
    }

    @Override
    public JDA getJDA()
    {
        return api;
    }

    @Override
    public List<Group> getGroups()
    {
        return Collections.unmodifiableList(
                new ArrayList<>(
                        groups.valueCollection()));
    }

    @Override
    public List<Group> getGroupsByName(String name, boolean ignoreCase)
    {
        return Collections.unmodifiableList(groups.valueCollection().stream()
                .filter(g -> g.getName() != null
                        && (ignoreCase
                            ? g.getName().equalsIgnoreCase(name)
                            : g.getName().equals(name)))
                .collect(Collectors.toList()));
    }

    @Override
    public Group getGroupById(String id)
    {
        return groups.get(Long.parseLong(id));
    }

    @Override
    public Group getGroupById(long id)
    {
        return groups.get(id);
    }

    @Override
    public List<Relationship> getRelationships()
    {
        return Collections.unmodifiableList(
                new ArrayList<>(
                        relationships.valueCollection()));
    }

    @Override
    public List<Relationship> getRelationships(RelationshipType type)
    {
        return Collections.unmodifiableList(relationships.valueCollection().stream()
                .filter(r -> r.getType().equals(type))
                .collect(Collectors.toList()));
    }

    @Override
    public List<Relationship> getRelationships(RelationshipType type, String name, boolean ignoreCase)
    {
        return Collections.unmodifiableList(relationships.valueCollection().stream()
                .filter(r -> r.getType().equals(type))
                .filter(r -> (ignoreCase
                        ? r.getUser().getName().equalsIgnoreCase(name)
                        : r.getUser().getName().equals(name)))
                .collect(Collectors.toList()));
    }

    @Override
    public List<Relationship> getRelationshipsByName(String name, boolean ignoreCase)
    {
        return Collections.unmodifiableList(relationships.valueCollection().stream()
                .filter(r -> (ignoreCase
                        ? r.getUser().getName().equalsIgnoreCase(name)
                        : r.getUser().getName().equals(name)))
                .collect(Collectors.toList()));
    }

    @Override
    public Relationship getRelationship(User user)
    {
        return getRelationshipById(user.getIdLong());
    }

    @Override
    public Relationship getRelationship(Member member)
    {
        return getRelationship(member.getUser());
    }

    @Override
    public Relationship getRelationshipById(String id)
    {
        return relationships.get(Long.parseLong(id));
    }

    @Override
    public Relationship getRelationshipById(long id)
    {
        return relationships.get(id);
    }

    @Override
    public Relationship getRelationshipById(String id, RelationshipType type)
    {
        Relationship relationship = getRelationshipById(id);
        if (relationship != null && relationship.getType() == type)
            return relationship;
        else
            return null;
    }

    @Override
    public Relationship getRelationshipById(long id, RelationshipType type)
    {
        Relationship relationship = getRelationshipById(id);
        if (relationship != null && relationship.getType() == type)
            return relationship;
        else
            return null;
    }


    @Override
    @SuppressWarnings("unchecked")
    public List<Friend> getFriends()
    {
        return (List<Friend>) (List) getRelationships(RelationshipType.FRIEND);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Friend> getFriendsByName(String name, boolean ignoreCase)
    {
        return (List<Friend>) (List) getRelationships(RelationshipType.FRIEND, name, ignoreCase);
    }

    @Override
    public Friend getFriend(User user)
    {
        return getFriendById(user.getIdLong());
    }

    @Override
    public Friend getFriend(Member member)
    {
        return getFriend(member.getUser());
    }

    @Override
    public Friend getFriendById(String id)
    {
        return (Friend) getRelationshipById(id, RelationshipType.FRIEND);
    }

    @Override
    public Friend getFriendById(long id)
    {
        return (Friend) getRelationshipById(id, RelationshipType.FRIEND);
    }

    @Override
    public UserSettings getSettings()
    {
        return userSettings;
    }

    public TLongObjectMap<Group> getGroupMap()
    {
        return groups;
    }

    public TLongObjectMap<Relationship> getRelationshipMap()
    {
        return relationships;
    }

    public TLongObjectMap<CallUser> getCallUserMap()
    {
        return callUsers;
    }

    @Override
    public RestAction<Invite> acceptInvite(Invite invite)
    {
        return acceptInvite(invite.getCode());
    }

    @Override
    public RestAction<Invite> acceptInvite(String code)
    {
        Args.notNull(code, "code");

        final Route.CompiledRoute route = Route.Invites.ACCEPT_INVITE.compile(code);

        return new RestAction<Invite>(api, route, null)
        {
            @Override
            protected void handleResponse(final Response response, final Request<Invite> request)
            {
                if (response.isOk())
                {
                    final Invite invite = EntityBuilder.get(this.api).createInvite(response.getObject());
                    request.onSuccess(invite);
                }
                else
                {
                    request.onFailure(response);
                }
            }
        };
    }
}
