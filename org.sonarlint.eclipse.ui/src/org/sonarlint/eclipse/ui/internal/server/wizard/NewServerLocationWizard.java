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
package org.sonarlint.eclipse.ui.internal.server.wizard;

import org.eclipse.core.runtime.jobs.Job;
import org.sonarlint.eclipse.core.internal.jobs.ServerUpdateJob;
import org.sonarlint.eclipse.core.internal.server.IServer;
import org.sonarlint.eclipse.core.internal.server.ServersManager;

public class NewServerLocationWizard extends AbstractServerLocationWizard {

  private static final String TITLE = "Connect to a SonarQube Server";

  public NewServerLocationWizard() {
    super(new ServerLocationWizardPage(), TITLE);
  }

  public NewServerLocationWizard(String serverId) {
    super(new ServerLocationWizardPage(serverId), TITLE);
  }

  @Override
  protected void doFinish(String serverId, String url, String username, String password) {
    IServer newServer = ServersManager.getInstance().create(serverId, url, username, password);
    ServersManager.getInstance().addServer(newServer, username, password);

    Job j = new ServerUpdateJob(newServer);
    j.schedule();
  }

}
