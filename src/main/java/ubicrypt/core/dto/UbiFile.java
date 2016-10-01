/**
 * Copyright (C) 2016 Giancarlo Frison <giancarlo@gfrison.com>
 * <p>
 * Licensed under the UbiCrypt License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://github.com/gfrison/ubicrypt/LICENSE.md
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ubicrypt.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Throwables;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ubicrypt.core.exp.ConflictException;

import javax.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class UbiFile<T extends UbiFile> implements Comparable<UbiFile> {

    protected UUID id = UUID.randomUUID();
    protected VClock vclock = new VClock();
    protected byte[] sha1;
    protected Instant lastModified = Instant.now();
    protected boolean deleted = false;
    protected boolean removed = false;
    protected boolean active = true;
    protected boolean ghost = false;
    protected Path path;
    protected long size;

    @JsonIgnore
    public abstract Optional<Key> getEncryption();

    @JsonIgnore
    public abstract String getName();

    public T copyFrom(UbiFile file) {
        id = UUID.fromString(file.getId().toString());
        try {
            vclock = (VClock) file.getVclock().clone();
        } catch (CloneNotSupportedException e) {
            Throwables.propagate(e);
        }
        sha1 = file.getSha1();
        lastModified = file.getLastModified();
        deleted = file.isDeleted();
        removed = file.isRemoved();
        active = file.isActive();
        ghost = file.isGhost();
        path = file.getPath();
        size = file.getSize();
        return (T) this;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UbiFile)) {
            return false;
        }
        final UbiFile localFile = (UbiFile) o;
        return new EqualsBuilder().append(id, localFile.id).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(id).toHashCode();
    }

    public UUID getId() {
        return id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public VClock getVclock() {
        return vclock;
    }

    public void setVclock(final VClock vclock) {
        this.vclock = vclock;
    }

    public byte[] getSha1() {
        return sha1;
    }

    public void setSha1(final byte[] sha1) {
        this.sha1 = sha1;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public void setLastModified(final Instant lastModified) {
        this.lastModified = lastModified;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(final boolean deleted) {
        this.deleted = deleted;
    }

    public boolean isRemoved() {
        return removed;
    }

    public void setRemoved(final boolean removed) {
        this.removed = removed;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(final boolean active) {
        this.active = active;
    }

    public Path getPath() {
        return path;
    }

    public void setPath(final Path path) {
        this.path = path;
    }

    public long getSize() {
        return size;
    }

    public void setSize(final long size) {
        this.size = size;
    }

    public boolean isGhost() {
        return ghost;
    }

    public void setGhost(boolean ghost) {
        this.ghost = ghost;
    }

    public VClock.Comparison compare(@NotNull UbiFile file) {
        checkNotNull(file, "file must be not null");
        return getVclock().compare(file.getVclock());
    }

    @Override
    public int compareTo(UbiFile file) {
        switch (compare(file)) {
            case older:
                return -1;
            case equal:
                return 0;
            case newer:
                return 1;
            default:
                throw new ConflictException(this, file);
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
                .append("id", id)
                .append("vclock", vclock)
                .append("lastModified", lastModified)
                .append("deleted", deleted)
                .append("removed", removed)
                .append("path", path)
                .append("size", size)
                .append("sha1", sha1)
                .append("active", active)
                .toString();
    }

    public enum KeyType {aes, pgp}
}
