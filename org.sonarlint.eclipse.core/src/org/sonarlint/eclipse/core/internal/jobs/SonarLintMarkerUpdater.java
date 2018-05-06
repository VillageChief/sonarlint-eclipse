/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2018 SonarSource SA
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
package org.sonarlint.eclipse.core.internal.jobs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.BadPositionCategoryException;
import org.eclipse.jface.text.DefaultPositionUpdater;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.TriggerType;
import org.sonarlint.eclipse.core.internal.markers.MarkerUtils;
import org.sonarlint.eclipse.core.internal.markers.MarkerUtils.ExtraPosition;
import org.sonarlint.eclipse.core.internal.resources.ProjectsProviderUtils;
import org.sonarlint.eclipse.core.internal.tracking.Trackable;
import org.sonarlint.eclipse.core.internal.utils.PreferencesUtils;
import org.sonarlint.eclipse.core.resource.ISonarLintFile;
import org.sonarlint.eclipse.core.resource.ISonarLintIssuable;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue.Flow;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueLocation;

public class SonarLintMarkerUpdater {

  private static final DefaultPositionUpdater EXTRA_POSITIONS_UPDATER = new DefaultPositionUpdater(MarkerUtils.SONARLINT_EXTRA_POSITIONS_CATEGORY);

  private SonarLintMarkerUpdater() {
  }

  public static void createOrUpdateMarkers(ISonarLintFile issuable, IDocument document, Collection<Trackable> issues, TriggerType triggerType, boolean createExtraLocations) {
    try {
      Set<IMarker> previousMarkersToDelete;
      if (triggerType.isOnTheFly()) {
        previousMarkersToDelete = new HashSet<>(Arrays.asList(issuable.getResource().findMarkers(SonarLintCorePlugin.MARKER_ON_THE_FLY_ID, false, IResource.DEPTH_ZERO)));
      } else {
        previousMarkersToDelete = Collections.emptySet();
      }

      if (createExtraLocations) {
        resetExtraPositions(document);
      }

      createOrUpdateMarkers(document, issuable, issues, triggerType, previousMarkersToDelete, createExtraLocations);

      for (IMarker marker : previousMarkersToDelete) {
        marker.delete();
      }
    } catch (CoreException e) {
      SonarLintLogger.get().error(e.getMessage(), e);
    }
  }

  public static Set<IResource> getResourcesWithMarkers(ISonarLintProject project) throws CoreException {
    return Arrays.stream(project.getResource().findMarkers(SonarLintCorePlugin.MARKER_ON_THE_FLY_ID, false, IResource.DEPTH_INFINITE))
      .map(IMarker::getResource)
      .collect(Collectors.toSet());
  }

  private static void resetExtraPositions(IDocument document) {
    try {
      document.removePositionCategory(MarkerUtils.SONARLINT_EXTRA_POSITIONS_CATEGORY);
    } catch (BadPositionCategoryException e1) {
      // Ignore
    }
    document.addPositionCategory(MarkerUtils.SONARLINT_EXTRA_POSITIONS_CATEGORY);
    document.addPositionUpdater(EXTRA_POSITIONS_UPDATER);
  }

  public static void clearMarkers(ISonarLintFile file) {
    resetExtraPositions(file.getDocument());
    try {
      file.getResource().deleteMarkers(SonarLintCorePlugin.MARKER_ON_THE_FLY_ID, false, IResource.DEPTH_ZERO);
    } catch (CoreException e) {
      SonarLintLogger.get().error(e.getMessage(), e);
    }
  }

  public static void updateMarkersWithServerSideData(ISonarLintIssuable issuable, IDocument document, Collection<Trackable> issues, TriggerType triggerType,
    boolean createExtraLocations) {
    try {
      for (Trackable issue : issues) {
        updateMarkerWithServerSideData(issuable, document, issue, triggerType, createExtraLocations);
      }
    } catch (CoreException e) {
      SonarLintLogger.get().error(e.getMessage(), e);
    }
  }

  private static void updateMarkerWithServerSideData(ISonarLintIssuable issuable, IDocument document, Trackable issue, TriggerType triggerType, boolean createExtraLocations)
    throws CoreException {
    Long markerId = issue.getMarkerId();
    IMarker marker = null;
    if (markerId != null) {
      marker = issuable.getResource().findMarker(markerId);
    }
    if (issue.isResolved()) {
      if (marker != null) {
        // Issue is associated to a marker, means it was not marked as resolved in previous analysis, but now it is, so clear marker
        marker.delete();
      }
      issue.setMarkerId(null);
    } else {
      if (marker != null) {
        updateServerMarkerAttributes(issue, marker);
      } else {
        // Issue was previously resolved, and is now reopen, so we need to recreate a marker
        createMarker(document, issuable, issue, triggerType, createExtraLocations);
      }
    }
  }

  private static void createOrUpdateMarkers(IDocument document, ISonarLintIssuable issuable, Collection<Trackable> issues,
    TriggerType triggerType, Set<IMarker> previousMarkersToDelete, boolean createExtraLocations) throws CoreException {
    for (Trackable issue : issues) {
      if (!issue.isResolved()) {
        if (!triggerType.isOnTheFly() || issue.getMarkerId() == null || issuable.getResource().findMarker(issue.getMarkerId()) == null) {
          createMarker(document, issuable, issue, triggerType, createExtraLocations);
        } else {
          IMarker marker = issuable.getResource().findMarker(issue.getMarkerId());
          updateMarkerAttributes(document, issue, marker, createExtraLocations);
          previousMarkersToDelete.remove(marker);
        }
      } else {
        issue.setMarkerId(null);
      }
    }
  }

  private static void createMarker(IDocument document, ISonarLintIssuable issuable, Trackable trackable, TriggerType triggerType, boolean createExtraLocations)
    throws CoreException {
    IMarker marker = issuable.getResource()
      .createMarker(triggerType.isOnTheFly() ? SonarLintCorePlugin.MARKER_ON_THE_FLY_ID : SonarLintCorePlugin.MARKER_REPORT_ID);
    if (triggerType.isOnTheFly()) {
      trackable.setMarkerId(marker.getId());
    }

    // See MarkerViewUtils
    marker.setAttribute("org.eclipse.ui.views.markers.name", issuable.getResourceNameForMarker());
    marker.setAttribute("org.eclipse.ui.views.markers.path", issuable.getResourceContainerForMarker());

    updateMarkerAttributes(document, trackable, marker, createExtraLocations);
  }

  private static void updateMarkerAttributes(IDocument document, Trackable trackable, IMarker marker, boolean createExtraLocations) throws CoreException {
    Map<String, Object> existingAttributes = marker.getAttributes();

    setMarkerAttributeIfDifferent(marker, existingAttributes, MarkerUtils.SONAR_MARKER_RULE_KEY_ATTR, trackable.getRuleKey());
    setMarkerAttributeIfDifferent(marker, existingAttributes, MarkerUtils.SONAR_MARKER_RULE_NAME_ATTR, trackable.getRuleName());
    setMarkerAttributeIfDifferent(marker, existingAttributes, IMarker.SEVERITY, PreferencesUtils.getMarkerSeverity());

    setMarkerAttributeIfDifferent(marker, existingAttributes, IMarker.MESSAGE, trackable.getMessage());

    // File level issues (line == null) are displayed on line 1
    setMarkerAttributeIfDifferent(marker, existingAttributes, IMarker.LINE_NUMBER, trackable.getLine() != null ? trackable.getLine() : 1);

    Position position = MarkerUtils.getPosition(document, trackable.getTextRange());
    if (position != null) {
      setMarkerAttributeIfDifferent(marker, existingAttributes, IMarker.CHAR_START, position.getOffset());
      setMarkerAttributeIfDifferent(marker, existingAttributes, IMarker.CHAR_END, position.getOffset() + position.getLength());
    }

    boolean hasExtraLocation = false;
    if (createExtraLocations) {
      hasExtraLocation = createExtraLocations(document, trackable, marker);
    }
    setMarkerAttributeIfDifferent(marker, existingAttributes, MarkerUtils.SONAR_MARKER_HAS_EXTRA_LOCATION_KEY_ATTR, hasExtraLocation);

    updateServerMarkerAttributes(trackable, marker);
  }

  private static boolean createExtraLocations(IDocument document, Trackable trackable, IMarker marker) {
    boolean hasExtraLocation = false;
    for (Flow f : trackable.getFlows()) {
      ExtraPosition parent = null;
      List<IssueLocation> locations = new ArrayList<>(f.locations());
      Collections.reverse(locations);
      for (IssueLocation l : locations) {
        ExtraPosition extraPosition = MarkerUtils.getExtraPosition(document,
          l.getStartLine(), l.getStartLineOffset(), l.getEndLine(), l.getEndLineOffset(),
          l.getMessage(),
          marker.getId(), parent);
        if (extraPosition != null) {
          savePosition(document, extraPosition);
          parent = extraPosition;
          hasExtraLocation = true;
        }
      }
    }
    return hasExtraLocation;
  }

  private static void savePosition(IDocument document, ExtraPosition extraPosition) {
    try {
      document.addPosition(MarkerUtils.SONARLINT_EXTRA_POSITIONS_CATEGORY, extraPosition);
    } catch (BadLocationException | BadPositionCategoryException e) {
      throw new IllegalStateException("Unable to register extra position", e);
    }
  }

  /**
   * After tracking issue from server, only a few attributes need to be updated:
   *   - severity (may be changed on server side)
   *   - server issue key
   *   - creation date
   */
  private static void updateServerMarkerAttributes(Trackable trackable, IMarker marker) throws CoreException {
    Map<String, Object> existingAttributes = marker.getAttributes();

    setMarkerAttributeIfDifferent(marker, existingAttributes, IMarker.PRIORITY, getPriority(trackable.getSeverity()));
    setMarkerAttributeIfDifferent(marker, existingAttributes, MarkerUtils.SONAR_MARKER_ISSUE_SEVERITY_ATTR, trackable.getSeverity());
    setMarkerAttributeIfDifferent(marker, existingAttributes, MarkerUtils.SONAR_MARKER_ISSUE_TYPE_ATTR, trackable.getType());
    setMarkerAttributeIfDifferent(marker, existingAttributes, MarkerUtils.SONAR_MARKER_SERVER_ISSUE_KEY_ATTR, trackable.getServerIssueKey());

    Long creationDate = trackable.getCreationDate();
    setMarkerAttributeIfDifferent(marker, existingAttributes, MarkerUtils.SONAR_MARKER_CREATION_DATE_ATTR, creationDate != null ? String.valueOf(creationDate.longValue()) : null);
  }

  private static void setMarkerAttributeIfDifferent(IMarker marker, @Nullable Map<String, Object> existingAttributes, String attributeName, @Nullable Object value)
    throws CoreException {
    if (!Objects.equals(value, existingAttributes != null ? existingAttributes.get(attributeName) : null)) {
      marker.setAttribute(attributeName, value);
    }
  }

  /**
   * @return Priority marker attribute. A number from the set of high, normal and low priorities defined by the platform.
   *
   * @see IMarker.PRIORITY_HIGH
   * @see IMarker.PRIORITY_NORMAL
   * @see IMarker.PRIORITY_LOW
   */
  private static int getPriority(final String severity) {
    switch (severity.toLowerCase(Locale.ENGLISH)) {
      case "blocker":
      case "critical":
        return IMarker.PRIORITY_HIGH;
      case "major":
        return IMarker.PRIORITY_NORMAL;
      case "minor":
      case "info":
      default:
        return IMarker.PRIORITY_LOW;
    }
  }

  public static void deleteAllMarkersFromReport() {
    ProjectsProviderUtils.allProjects().stream()
      .filter(ISonarLintProject::isOpen)
      .forEach(p -> p.deleteAllMarkers(SonarLintCorePlugin.MARKER_REPORT_ID));
  }
}
