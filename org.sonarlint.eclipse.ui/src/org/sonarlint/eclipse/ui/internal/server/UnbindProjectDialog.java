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
package org.sonarlint.eclipse.ui.internal.server;

import java.util.List;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Shell;
import org.sonarlint.eclipse.core.internal.resources.SonarLintProject;
import org.sonarlint.eclipse.core.internal.server.IServer;
import org.sonarlint.eclipse.core.internal.server.ServersManager;
import org.sonarlint.eclipse.ui.internal.Messages;
import org.sonarlint.eclipse.ui.internal.SonarLintUiPlugin;

/**
 * Dialog that prompts a user to unbind project(s).
 */
public class UnbindProjectDialog extends MessageDialog {
  protected List<SonarLintProject> projects;

  public UnbindProjectDialog(Shell parentShell, List<SonarLintProject> projects) {
    super(parentShell, Messages.unbindProjectDialogTitle, null, getMessage(projects), QUESTION, new String[] {IDialogConstants.OK_LABEL, IDialogConstants.CANCEL_LABEL}, 0);
    this.projects = projects;

  }

  private static String getMessage(List<SonarLintProject> projects) {
    StringBuilder sb = new StringBuilder();
    if (projects.size() == 1) {
      sb.append(NLS.bind(Messages.unbindProjectDialogMessage, projects.get(0).getProject().getName()));
    } else {
      sb.append(NLS.bind(Messages.unbindProjectDialogMessageMany, Integer.toString(projects.size())));
    }
    return sb.toString();
  }

  @Override
  protected void buttonPressed(int buttonId) {
    if (buttonId == OK && !projects.isEmpty()) {
      Job job = new UnbindProjectJob();
      job.setPriority(Job.BUILD);
      job.schedule();
    }
    super.buttonPressed(buttonId);
  }

  private class UnbindProjectJob extends Job {
    UnbindProjectJob() {
      super(Messages.unbindProjectTask);
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
      try {
        for (SonarLintProject project : projects) {
          if (monitor.isCanceled()) {
            return Status.CANCEL_STATUS;
          }
          IServer oldServer = ServersManager.getInstance().getServer(project.getServerId());
          project.unbind();
          oldServer.notifyAllListeners();
        }
      } catch (Exception e) {
        return new Status(IStatus.ERROR, SonarLintUiPlugin.PLUGIN_ID, 0, e.getMessage(), e);
      }
      return Status.OK_STATUS;
    }
  }
}
