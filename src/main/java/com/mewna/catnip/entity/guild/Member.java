/*
 * Copyright (c) 2018 amy, All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.mewna.catnip.entity.guild;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.mewna.catnip.cache.view.CacheView;
import com.mewna.catnip.entity.Snowflake;
import com.mewna.catnip.entity.channel.DMChannel;
import com.mewna.catnip.entity.channel.GuildChannel;
import com.mewna.catnip.entity.impl.MemberImpl;
import com.mewna.catnip.entity.util.Permission;
import com.mewna.catnip.util.PermissionUtil;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * A member in a guild.
 *
 * @author amy
 * @since 9/4/18.
 */
@SuppressWarnings("unused")
@JsonDeserialize(as = MemberImpl.class)
public interface Member extends Snowflake {
    /**
     * The id of the guild this member is from.
     *
     * @return String representing the guild ID.
     */
    @Nonnull
    @CheckReturnValue
    default String guildId() {
        return Long.toUnsignedString(guildIdAsLong());
    }
    
    /**
     * The id of the guild this member is from.
     *
     * @return Long representing the guild ID.
     */
    @CheckReturnValue
    long guildIdAsLong();
    
    /**
     * The user's nickname in this guild.
     *
     * @return User's nickname. Null if not set.
     */
    @Nullable
    @CheckReturnValue
    String nick();
    
    /**
     * The ids of the user's roles in this guild.
     *
     * @return A {@link Set} of the ids of the user's roles.
     */
    @Nonnull
    @CheckReturnValue
    Set<String> roleIds();
    
    /**
     * The user's roles in this guild.
     *
     * @return A {@link Set} of the user's roles.
     */
    @Nonnull
    @CheckReturnValue
    default Set<Role> roles() {
        final CacheView<Role> roles = catnip().cache().roles(guildId());
        return Collections.unmodifiableSet(roleIds().stream()
                .map(roles::getById)
                .collect(Collectors.toSet()));
    }
    
    /**
     * Whether the user is voice muted.
     * <br>Voice muted user cannot transmit voice.
     *
     * @return True if muted, false otherwise.
     */
    @CheckReturnValue
    boolean mute();
    
    /**
     * Whether the user is deafened.
     * <br>Deafened users cannot receive nor send voice.
     *
     * @return True if deafened, false otherwise.
     */
    @CheckReturnValue
    boolean deaf();
    
    /**
     * When the user joined the server last.
     * <br>Members who have joined, left, then rejoined will only have the most
     * recent join exposed.
     * <br>This may be null under some conditions, ex. a member leaving a
     * guild. In cases like this, catnip will attempt to load the old data from
     * the cache if possible, but it may not work, hence nullability.
     *
     * @return The {@link OffsetDateTime date and time} the member joined the guild.
     */
    @Nullable
    @CheckReturnValue
    OffsetDateTime joinedAt();
    
    /**
     * The member's color, as shown in the official Discord Client, or {@code null} if they have no roles with a color.
     * <br>This will iterate over all the roles this member has, so try to avoid calling this method multiple times
     * if you only need the value once.
     *
     * @return A {@link Color color} representing the member's color, as shown in the official Discord Client.
     */
    @Nullable
    @CheckReturnValue
    default Color color() {
        int color = -1;
        int highest = Integer.MIN_VALUE;
        long roleId = 0;
        
        final CacheView<Role> cache = catnip().cache().roles(guildId());
        for (final String id : roleIds()) {
            final Role role = cache.getById(id);
            if (role != null && role.color() != 0) {
                if (role.position() == highest) {
                    final long idAsLong = role.idAsLong();
                    if (idAsLong > roleId) {
                        color = role.color();
                        highest = role.position();
                        roleId = idAsLong;
                    }
                } else if (role.position() > highest) {
                    color = role.color();
                    highest = role.position();
                    roleId = role.idAsLong();
                }
            }
        }
        return color == -1 ? null : new Color(color);
    }
    
    /**
     * Creates a DM channel with this member's user.
     *
     * @return Future with the result of the DM creation.
     */
    @JsonIgnore
    @CheckReturnValue
    default CompletionStage<DMChannel> createDM() {
        return catnip().rest().user().createDM(id());
    }
    
    default Set<Permission> permissions() {
        return Permission.toSet(PermissionUtil.effectivePermissions(catnip(), this));
    }
    
    default Set<Permission> permissions(@Nonnull final GuildChannel channel) {
        return Permission.toSet(PermissionUtil.effectivePermissions(catnip(), this, channel));
    }
    
    default boolean hasPermissions(@Nonnull final Permission... permissions) {
        return hasPermissions(Arrays.asList(permissions));
    }
    
    default boolean hasPermissions(@Nonnull final Collection<Permission> permissions) {
        final long needed = Permission.from(permissions);
        final long actual = PermissionUtil.effectivePermissions(catnip(), this);
        return (actual & needed) == needed;
    }
    
    default boolean hasPermissions(@Nonnull final GuildChannel channel, @Nonnull final Permission... permissions) {
        return hasPermissions(channel, Arrays.asList(permissions));
    }
    
    default boolean hasPermissions(@Nonnull final GuildChannel channel, @Nonnull final Collection<Permission> permissions) {
        final long needed = Permission.from(permissions);
        final long actual = PermissionUtil.effectivePermissions(catnip(), this, channel);
        return (actual & needed) == needed;
    }
}
