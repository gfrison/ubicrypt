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
package ubicrypt.core.remote;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;

import rx.Observable;
import ubicrypt.core.FileProvenience;
import ubicrypt.core.dto.RemoteConfig;

public interface IRemoteAction
  extends BiPredicate<FileProvenience, RemoteConfig>,
  BiFunction<FileProvenience, RemoteConfig, Observable<Boolean>> {
}
