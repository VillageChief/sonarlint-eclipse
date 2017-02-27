/*
 * CodeScan for Eclipse
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
package org.sonarlint.eclipse.ui.internal;

import java.util.Arrays;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.sonarlint.eclipse.core.internal.SonarLintChangeListener;
import org.sonarlint.eclipse.core.internal.TriggerType;
import org.sonarlint.eclipse.core.internal.jobs.AnalyzeProjectJob;
import org.sonarlint.eclipse.core.internal.jobs.AnalyzeProjectRequest;
import org.sonarlint.eclipse.core.internal.resources.SonarLintProject;
import org.sonarlint.eclipse.core.internal.utils.SonarLintUtils;
import org.sonarlint.eclipse.ui.internal.views.issues.ChangeSetIssuesView;

public class SonarLintPartListener implements IPartListener2 {
  @Override
  public void partOpened(IWorkbenchPartReference partRef) {
    IWorkbenchPart part = partRef.getPart(true);
    if (part instanceof IEditorPart) {
      IEditorInput input = ((IEditorPart) part).getEditorInput();
      if (input instanceof IFileEditorInput) {
        IResource resource = ((IFileEditorInput) input).getFile();
        scheduleUpdate(resource);
      }
    }
    ChangeSetIssuesView.notifyEditorChanged();
  }

  private static void scheduleUpdate(IResource resource) {
    IFile file = (IFile) resource.getAdapter(IFile.class);
    if (file != null) {
      IFile specificFile = SonarLintUtils.toSpecificFile(file);
      if (!specificFile.equals(file)) {
        // Don't analyze files that are also part of submodules
        return;
      }
      final SonarLintProject sonarProject = SonarLintProject.getInstance(file.getProject());
      if (!sonarProject.isAutoEnabled() || !SonarLintChangeListener.shouldAnalyze(file)) {
        return;
      }
      AnalyzeProjectRequest request = new AnalyzeProjectRequest(resource.getProject(), Arrays.asList(file), TriggerType.EDITOR_OPEN);
      new AnalyzeProjectJob(request).schedule();
    }
  }

  @Override
  public void partVisible(IWorkbenchPartReference partRef) {
    ChangeSetIssuesView.notifyEditorChanged();
  }

  @Override
  public void partInputChanged(IWorkbenchPartReference partRef) {
    ChangeSetIssuesView.notifyEditorChanged();
  }

  @Override
  public void partHidden(IWorkbenchPartReference partRef) {
    ChangeSetIssuesView.notifyEditorChanged();
  }

  @Override
  public void partDeactivated(IWorkbenchPartReference partRef) {
    ChangeSetIssuesView.notifyEditorChanged();
  }

  @Override
  public void partClosed(IWorkbenchPartReference partRef) {
    ChangeSetIssuesView.notifyEditorChanged();
  }

  @Override
  public void partBroughtToTop(IWorkbenchPartReference partRef) {
    ChangeSetIssuesView.notifyEditorChanged();
  }

  @Override
  public void partActivated(IWorkbenchPartReference partRef) {
    ChangeSetIssuesView.notifyEditorChanged();
  }

}
