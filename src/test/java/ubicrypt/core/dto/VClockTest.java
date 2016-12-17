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

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VClockTest {
  @Test
  public void testName() throws Exception {
    final VClock v1 = new VClock();
    v1.increment(1);
    final VClock v2 = new VClock();
    v2.increment(1);
    assertThat(v1.compare(v2)).isEqualTo(VClock.Comparison.equal);
  }

  @Test
  public void test2() throws Exception {
    final VClock v1 = new VClock();
    final VClock v2 = new VClock();
    assertThat(v1.compare(v2)).isEqualTo(VClock.Comparison.equal);
  }

  @Test
  public void test3() throws Exception {
    final VClock v1 = new VClock();
    v1.increment(1);
    final VClock v2 = new VClock();
    v2.increment(2);
    assertThat(v1.compare(v2)).isEqualTo(VClock.Comparison.conflict);
  }

  @Test
  public void test4() throws Exception {
    final VClock v1 = new VClock();
    v1.increment(1);
    v1.increment(1);
    final VClock v2 = new VClock();
    v2.increment(1);
    assertThat(v1.compare(v2)).isEqualTo(VClock.Comparison.newer);
  }

  @Test
  public void test5() throws Exception {
    final VClock v1 = new VClock();
    v1.increment(1);
    final VClock v2 = new VClock();
    v2.increment(1);
    v2.increment(1);
    assertThat(v1.compare(v2)).isEqualTo(VClock.Comparison.older);
  }

  @Test
  public void test6() throws Exception {
    final VClock v1 = new VClock();
    v1.increment(1);
    v1.increment(1);
    v1.increment(2);
    final VClock v2 = new VClock();
    v2.increment(1);
    v2.increment(2);
    v2.increment(2);
    assertThat(v1.compare(v2)).isEqualTo(VClock.Comparison.conflict);
  }

  @Test
  public void testClone() throws Exception {
    final VClock v1 = new VClock();
    v1.increment(1);
    v1.increment(1);
    v1.increment(2);
    final VClock v2 = (VClock) v1.clone();
    assertThat(v1.compare(v2)).isEqualTo(VClock.Comparison.equal);
    v1.increment(2);
    assertThat(v1.compare(v2)).isEqualTo(VClock.Comparison.newer);
  }
}
