/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2017 SonarSource SA
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
package org.sonarlint.eclipse.ui.internal.properties;

import java.util.Arrays;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.ui.dialogs.PropertyPage;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.adapter.Adapters;
import org.sonarlint.eclipse.core.internal.resources.SonarLintProjectConfiguration;
import org.sonarlint.eclipse.core.internal.server.IServer;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarlint.eclipse.ui.internal.Messages;
import org.sonarlint.eclipse.ui.internal.bind.BindProjectsWizard;
import org.sonarlint.eclipse.ui.internal.server.wizard.ServerConnectionWizard;

/**
 * Property page for projects. It store in
 * <project>/.settings/org.sonarlint.eclipse.prefs following properties
 *
 */
public class SonarLintProjectPropertyPage extends PropertyPage {

  private Button enabledBtn;
  private Link addServerLink;
  private Link bindLink;
  private Composite container;
  private Label boundDetails;

  public SonarLintProjectPropertyPage() {
    setTitle(Messages.SonarProjectPropertyPage_title);
  }

  @Override
  protected Control createContents(Composite parent) {
    if (parent == null) {
      return new Composite(parent, SWT.NULL);
    }

    final SonarLintProjectConfiguration projectConfig = SonarLintProjectConfiguration.read(getProject().getScopeContext());

    container = new Composite(parent, SWT.NULL);
    GridLayout layout = new GridLayout();
    container.setLayout(layout);
    container.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, true));
    layout.numColumns = 2;
    layout.verticalSpacing = 9;

    enabledBtn = new Button(container, SWT.CHECK);
    enabledBtn.setText("Run SonarLint automatically");
    enabledBtn.setSelection(projectConfig.isAutoEnabled());
    GridData layoutData = new GridData();
    layoutData.horizontalSpan = 2;
    enabledBtn.setLayoutData(layoutData);

    boundDetails = new Label(container, SWT.NONE);
    boundDetails.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    addServerLink = new Link(container, SWT.NONE);
    GridData gd = new GridData(SWT.LEFT, SWT.FILL, true, false);
    gd.horizontalSpan = 2;
    addServerLink.setLayoutData(gd);
    addServerLink.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        String serverId = SonarLintProjectConfiguration.read(getProject().getScopeContext()).getServerId();
        WizardDialog wd = ServerConnectionWizard.createDialog(container.getShell(), serverId);
        if (wd.open() == Window.OK) {
          updateState();
        }
      }
    });

    bindLink = new Link(container, SWT.NONE);
    gd = new GridData(SWT.LEFT, SWT.FILL, true, false);
    gd.horizontalSpan = 2;
    bindLink.setLayoutData(gd);
    bindLink.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        BindProjectsWizard wizard = new BindProjectsWizard(Arrays.asList(getProject()));
        WizardDialog wd = new WizardDialog(container.getShell(), wizard);
        if (wd.open() == Window.OK) {
          updateState();
        }
      }
    });

    updateState();

    return container;
  }

  private void updateState() {
    final SonarLintProjectConfiguration projectConfig = SonarLintProjectConfiguration.read(getProject().getScopeContext());
    String moduleKey = projectConfig.getModuleKey();
    final String serverId = projectConfig.getServerId();
    if (moduleKey == null && serverId == null) {
      bindLink.setText("<a>Bind this Eclipse project to a CodeScan project</a>");
      boundDetails.setText("");
    } else {
      boundDetails.setText("Bound to the project '" + moduleKey + "' on server '" + serverName(serverId) + "'");
      bindLink.setText("<a>Update project binding</a>");
    }
    if (serverId != null && SonarLintCorePlugin.getServersManager().getServer(serverId) == null) {
      addServerLink.setText("<a>Connect to CodeScan server '" + serverId + "'</a>");
      addServerLink.setVisible(true);
    } else {
      addServerLink.setVisible(false);
    }
    container.layout(true, true);
  }

  private static String serverName(final String serverId) {
    if (serverId == null) {
      return "";
    }
    IServer server = SonarLintCorePlugin.getServersManager().getServer(serverId);
    return server != null ? server.getId() : ("Unknown server: '" + serverId + "'");
  }

  @Override
  protected void performDefaults() {
    enabledBtn.setEnabled(true);
    super.performDefaults();
  }

  @Override
  public boolean performOk() {
    final SonarLintProjectConfiguration projectConfig = SonarLintProjectConfiguration.read(getProject().getScopeContext());
    projectConfig.setAutoEnabled(enabledBtn.getSelection());
    projectConfig.save();
    return super.performOk();
  }

  private ISonarLintProject getProject() {
    return Adapters.adapt(getElement(), ISonarLintProject.class);
  }
}
