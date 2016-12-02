/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2016 SonarSource SA
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarlint.eclipse.core.internal.tracking;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import javax.annotation.CheckForNull;

public class IssueTracker {

  private final IssueTrackerCache cache;

  public IssueTracker(IssueTrackerCache cache) {
    this.cache = cache;
  }

  /**
   * Match a new set of trackables to current state.
   * If this is the first analysis, leave creation date as null.
   *
   * @param file
   * @param trackables
   */
  public synchronized Collection<Trackable> matchAndTrackAsNew(String file, Collection<Trackable> trackables) {
    Collection<Trackable> tracked;
    if (cache.isFirstAnalysis(file)) {
      tracked = trackables;
    } else {
      tracked = matchAndTrack(cache.getCurrentTrackables(file), trackables);
    }
    cache.put(file, tracked);
    return tracked;
  }

  /**
   * "Rebase" current trackables against given trackables.
   *
   * @param file
   * @param trackables
   */
  @CheckForNull
  public synchronized Collection<Trackable> matchAndTrackAsBase(String file, Collection<Trackable> trackables) {
    // store issues (ProtobufIssueTrackable) are of no use since they can't be used in markers. There should have been
    // an analysis before that set the live issues for the file (even if it is empty)
    Collection<Trackable> current = cache.getLiveOrFail(file);
    if (current.isEmpty()) {
      // whatever is the base, if current is empty, then nothing to do
      return Collections.emptyList();
    }
    Collection<Trackable> tracked = matchAndTrack(trackables, current);
    cache.put(file, tracked);
    return tracked;
  }

  // note: the base issues are type T: sometimes mutable, sometimes not (for example server issues)
  private static Collection<Trackable> matchAndTrack(Collection<Trackable> baseIssues, Collection<Trackable> nextIssues) {
    Collection<Trackable> trackedIssues = new ArrayList<>();
    Tracking<Trackable, Trackable> tracking = new Tracker<Trackable, Trackable>().track(() -> nextIssues, () -> baseIssues);
    for (Map.Entry<Trackable, Trackable> entry : tracking.getMatchedRaws().entrySet()) {
      Trackable next = new CombinedTrackable(entry.getValue(), entry.getKey());
      trackedIssues.add(next);
    }
    for (Trackable next : tracking.getUnmatchedRaws()) {
      if (next.getServerIssueKey() != null) {
        next = new DisconnectedTrackable(next);
      } else {
        next = new LeakedTrackable(next);
      }
      trackedIssues.add(next);
    }
    return trackedIssues;
  }

  public void clear() {
    cache.clear();
  }

  public void shutdown() {
    cache.shutdown();
  }
}
