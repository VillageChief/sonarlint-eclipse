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
package org.sonarlint.eclipse.core.internal.jobs;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.resources.SonarLintProject;
import org.sonarlint.eclipse.core.internal.server.IServer;

public class ServerUpdateJob extends Job {
  private final IServer server;

  public ServerUpdateJob(IServer server) {
    super("Update data from SonarQube server '" + server.getId() + "'");
    this.server = server;
  }

  @Override
  protected IStatus run(IProgressMonitor monitor) {
    List<SonarLintProject> projectsToUpdate = server.getBoundProjects();
    monitor.beginTask("Update server and all associated projects", projectsToUpdate.size() + 1);
    try {
      server.updateStorage(monitor);
    } catch (Exception e) {
      return new Status(IStatus.ERROR, SonarLintCorePlugin.PLUGIN_ID, "Unable to update data from server '" + server.getId() + "'", e);
    }
    monitor.worked(1);
    List<IStatus> failures = new ArrayList<>();
    for (SonarLintProject projectToUpdate : projectsToUpdate) {
      if (monitor.isCanceled()) {
        return Status.CANCEL_STATUS;
      }
      try {
        server.updateProjectStorage(projectToUpdate.getModuleKey());
      } catch (Exception e) {
        failures.add(new Status(IStatus.ERROR, SonarLintCorePlugin.PLUGIN_ID, "Unable to update binding for project '" + projectToUpdate.getProject().getName() + "'", e));
      }
      monitor.worked(1);
    }
    monitor.done();
    if (!failures.isEmpty()) {
      return new MultiStatus(SonarLintCorePlugin.PLUGIN_ID, IStatus.ERROR, failures.toArray(new IStatus[0]), "Failed to update binding for " + failures.size() + " project(s)",
        null);
    }
    return Status.OK_STATUS;
  }
}
