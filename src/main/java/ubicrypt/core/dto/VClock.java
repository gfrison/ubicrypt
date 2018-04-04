/*
 * Copyright (C) 2016 Giancarlo Frison <giancarlo@gfrison.com>
 *
 * Licensed under the UbiCrypt License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://github.com/gfrison/ubicrypt/LICENSE.md
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ubicrypt.core.dto;

import com.google.common.collect.ImmutableMap;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class VClock implements Cloneable {
  final ConcurrentHashMap<Integer, AtomicLong> map;
  private transient Consumer<Void> observer;

  public VClock() {
    this.map = new ConcurrentHashMap<>();
  }

  private VClock(ConcurrentHashMap<Integer, AtomicLong> map, Consumer<Void> observer) {
    this.map = map;
    this.observer = observer;
  }

  public void increment(int device) {
    map.computeIfAbsent(device, i -> new AtomicLong(0)).incrementAndGet();
    if (observer != null) {
      observer.accept(null);
    }
  }

  public Comparison compare(VClock v2) {
    if (map.size() > v2.getMap().size()) {
      return Comparison.newer;
    }
    if (v2.getMap().size() > map.size()) {
      return Comparison.older;
    }
    for (int node : map.keySet()) {
      if (v2.map.get(node) == null) {
        return Comparison.conflict;
      }
    }
    boolean v1Bigger = false;
    boolean v2Bigger = false;
    for (int node : map.keySet()) {
      if (v1Bigger && v2Bigger) {
        break;
      }
      long v1Version = map.get(node).get();
      long v2Version = v2.getMap().get(node).get();
      if (v1Version > v2Version) {
        v1Bigger = true;
      } else if (v1Version < v2Version) {
        v2Bigger = true;
      }
    }
    if (!v1Bigger && !v2Bigger) {
      return Comparison.equal;
    }
    /* This is the case where v1 is a successor clock to v2 */
    else if (v1Bigger && !v2Bigger) {
      return Comparison.newer;
    }
    /* This is the case where v2 is a successor clock to v1 */
    else if (!v1Bigger && v2Bigger) {
      return Comparison.older;
    }
    /* This is the case where both clocks are parallel to one another */
    else {
      return Comparison.conflict;
    }
  }

  public Map<Integer, AtomicLong> getMap() {
    return ImmutableMap.copyOf(map);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
        .append("map", map)
        .toString();
  }

  @Override
  public Object clone() {
    return new VClock(
        map.entrySet()
            .stream()
            .collect(
                ConcurrentHashMap::new,
                (ConcurrentHashMap<Integer, AtomicLong> map,
                 Map.Entry<Integer, AtomicLong> entry) ->
                    map.put(entry.getKey(), new AtomicLong(entry.getValue().longValue())),
                (ConcurrentHashMap<Integer, AtomicLong> map1,
                 ConcurrentHashMap<Integer, AtomicLong> map2) ->
                    map1.putAll(map2)), observer);
  }

  public void setObserver(Consumer<Void>observer) {
    this.observer = observer;
  }

  public enum Comparison {
    equal,
    older,
    newer,
    conflict
  }
}
