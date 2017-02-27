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
package org.sonarlint.eclipse.ui.internal.server.wizard;

import java.net.MalformedURLException;
import java.net.URL;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.server.IServer;
import org.sonarlint.eclipse.core.internal.server.ServersManager;
import org.sonarlint.eclipse.core.internal.utils.StringUtils;
import org.sonarlint.eclipse.ui.internal.Messages;
import org.sonarlint.eclipse.ui.internal.SonarLintImages;
import org.sonarlint.eclipse.ui.internal.SonarLintUiPlugin;

public class ServerLocationWizardPage extends WizardPage {
  private static final String DEFAULT_URL_SUGGESTION = "https://";

  private static final String ERROR_READING_SECURE_STORAGE = "Error reading secure storage";

  private final IServer server;

  private Text serverIdText;
  private Text serverUrlText;
  private Text serverUsernameText;
  private Text serverPasswordText;
  private IStatus status;
  private final boolean edit;
  private boolean serverIdManuallyChanged;

  private ModifyListener idModifyListener;

  private final String defaultServerId;

  public ServerLocationWizardPage() {
    this((IServer) null);
  }

  public ServerLocationWizardPage(String defaultServerId) {
    this((IServer) null, defaultServerId);
  }

  public ServerLocationWizardPage(IServer sonarServer) {
    this(sonarServer, null);
  }

  public ServerLocationWizardPage(IServer sonarServer, String defaultServerId) {
    super("server_location_page", "SonarQube Server Configuration", SonarLintImages.IMG_WIZBAN_NEW_SERVER);
    this.edit = sonarServer != null;
    this.server = sonarServer;
    this.defaultServerId = defaultServerId;
  }

  /**
   * @see org.eclipse.jface.dialogs.IDialogPage#createControl(Composite)
   */
  @Override
  public void createControl(Composite parent) {
    final FormToolkit toolkit = new FormToolkit(parent.getDisplay());
    parent.addDisposeListener(e -> toolkit.dispose());
    final ScrolledForm form = toolkit.createScrolledForm(parent);
    form.setBackground(parent.getBackground());

    GridLayout layout = new GridLayout();
    layout.numColumns = 3;
    layout.verticalSpacing = 9;
    form.getBody().setLayout(layout);

    GridData layoutData = new GridData(SWT.FILL, SWT.FILL, true, true);
    form.setLayoutData(layoutData);

    // SonarQube Server URL
    createServerUrlField(form);

    createServerIdField(form);

    // Sonar Server Username/Token
    createUsernameField(parent, form);
    createOpenSecurityPageButton(form.getBody());

    // Sonar Server password
    createPasswordField(form);

    // Test connection button
    createTestConnectionButton(form.getBody());

    if (edit) {
      dialogChanged();
    }
    Dialog.applyDialogFont(parent);
    setControl(form.getBody());
  }

  private void createPasswordField(final ScrolledForm form) {
    Label labelPassword = new Label(form.getBody(), SWT.NULL);
    labelPassword.setText(Messages.ServerLocationWizardPage_label_password);
    serverPasswordText = new Text(form.getBody(), SWT.BORDER | SWT.SINGLE | SWT.PASSWORD);
    GridData gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 2;
    serverPasswordText.setLayoutData(gd);
    if (edit && server.hasAuth()) {
      String previousPassword;
      try {
        previousPassword = ServersManager.getPassword(server);
        if (previousPassword != null) {
          serverPasswordText.setText(previousPassword);
        }
      } catch (StorageException e) {
        SonarLintCorePlugin.getDefault().error(ERROR_READING_SECURE_STORAGE, e);
        MessageDialog.openError(this.getShell(), ERROR_READING_SECURE_STORAGE, "Unable to read password from secure storage: " + e.getMessage());
      }
    }
  }

  private void createUsernameField(Composite parent, final ScrolledForm form) {
    Label labelUsername = new Label(form.getBody(), SWT.NULL);
    labelUsername.setText(Messages.ServerLocationWizardPage_label_username);
    serverUsernameText = new Text(form.getBody(), SWT.BORDER | SWT.SINGLE);
    serverUsernameText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    if (edit && server.hasAuth()) {
      String previousUsername;
      try {
        previousUsername = ServersManager.getUsername(server);
        if (previousUsername != null) {
          serverUsernameText.setText(previousUsername);
        }
      } catch (StorageException e) {
        SonarLintCorePlugin.getDefault().error(ERROR_READING_SECURE_STORAGE, e);
        MessageDialog.openError(parent.getDisplay().getActiveShell(), ERROR_READING_SECURE_STORAGE, "Unable to read username from secure storage: " + e.getMessage());
      }
    }
  }

  private void createServerUrlField(final ScrolledForm form) {
    Label labelUrl = new Label(form.getBody(), SWT.NULL);
    labelUrl.setText(Messages.ServerLocationWizardPage_label_host);
    serverUrlText = new Text(form.getBody(), SWT.BORDER | SWT.SINGLE);
    GridData gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 2;
    serverUrlText.setLayoutData(gd);
    if (edit) {
      serverUrlText.setText(StringUtils.defaultString(server.getHost()));
    } else {
      serverUrlText.setText(DEFAULT_URL_SUGGESTION);
    }
    serverUrlText.setFocus();
    serverUrlText.setSelection(serverUrlText.getText().length());
    serverUrlText.addModifyListener(e -> {
      if (!edit && !serverIdManuallyChanged) {
        try {
          URL url = new URL(serverUrlText.getText());
          serverIdText.removeModifyListener(idModifyListener);
          serverIdText.setText(StringUtils.substringBefore(url.getHost(), "."));
          serverIdText.addModifyListener(idModifyListener);
        } catch (MalformedURLException e1) {
          // Ignore
        }
      }
      dialogChanged();
    });
  }

  private void createServerIdField(final ScrolledForm form) {
    boolean isEditable = !edit;
    Label labelId = new Label(form.getBody(), SWT.NULL);
    labelId.setText(Messages.ServerLocationWizardPage_label_id);
    serverIdText = new Text(form.getBody(), isEditable ? (SWT.BORDER | SWT.SINGLE) : (SWT.BORDER | SWT.READ_ONLY));
    GridData gdId = new GridData(GridData.FILL_HORIZONTAL);
    gdId.horizontalSpan = 2;
    serverIdText.setLayoutData(gdId);
    serverIdText.setEnabled(isEditable);
    serverIdText.setEditable(isEditable);
    if (edit) {
      serverIdText.setText(StringUtils.defaultString(server.getId()));
    } else {
      if (defaultServerId != null) {
        serverIdText.setText(defaultServerId);
        serverIdManuallyChanged = true;
      } else {
        serverIdManuallyChanged = false;
      }
    }
    idModifyListener = e -> {
      serverIdManuallyChanged = true;
      dialogChanged();
    };
    serverIdText.addModifyListener(idModifyListener);
  }

  private void createTestConnectionButton(Composite container) {
    Button testConnectionButton = new Button(container, SWT.PUSH);
    testConnectionButton.setText(Messages.ServerLocationWizardPage_action_test);
    testConnectionButton.setToolTipText(Messages.ServerLocationWizardPage_action_test_tooltip);
    GridData gd = new GridData();
    gd.horizontalSpan = 3;
    gd.horizontalAlignment = GridData.CENTER;
    testConnectionButton.setLayoutData(gd);

    testConnectionButton.addListener(SWT.Selection, e -> {
      try {
        ServerConnectionTestJob testJob = new ServerConnectionTestJob(transcientServer(), getUsername(), getPassword());
        getWizard().getContainer().run(true, true, testJob);
        status = testJob.getStatus();
      } catch (OperationCanceledException e1) {
        status = Status.CANCEL_STATUS;
      } catch (Exception e1) {
        status = new Status(IStatus.ERROR, SonarLintUiPlugin.PLUGIN_ID, Messages.ServerLocationWizardPage_msg_error + " " + e1.getMessage(), e1);
      }
      getWizard().getContainer().updateButtons();

      String message = status.getMessage();
      if (status.getSeverity() == IStatus.OK) {
        setMessage(message, IMessageProvider.INFORMATION);
      } else {
        setMessage(message, IMessageProvider.ERROR);
      }
    });
  }

  private void createOpenSecurityPageButton(Composite container) {
    Button button = new Button(container, SWT.PUSH);
    button.setText(Messages.ServerLocationWizardPage_action_token);
    button.setToolTipText(Messages.ServerLocationWizardPage_action_token_tooltip);
    button.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));

    button.addListener(SWT.Selection, e -> generateToken());
  }

  private void generateToken() {
    if (StringUtils.isBlank(getServerUrl())) {
      MessageDialog.openError(this.getShell(), "Invalid Server URL", "Please fill the 'Server Url' field");
      return;
    }

    StringBuilder url = new StringBuilder(256);
    url.append(getServerUrl());

    url.append("/account/security");
    try {
      PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(new URL(url.toString()));
    } catch (PartInitException | MalformedURLException e) {
      SonarLintCorePlugin.getDefault().error("Unable to open external browser", e);
      MessageDialog.openError(this.getShell(), "Error", "Unable to open external browser: " + e.getMessage());
    }
  }

  private void dialogChanged() {
    updateStatus(ServersManager.getInstance().validate(getServerId(), getServerUrl(), edit));
  }

  private void updateStatus(String message) {
    setErrorMessage(message);
    setPageComplete(message == null);
  }

  public String getServerId() {
    return serverIdText.getText();
  }

  public String getServerUrl() {
    return StringUtils.removeEnd(serverUrlText.getText(), "/");
  }

  public String getUsername() {
    return serverUsernameText.getText();
  }

  public String getPassword() {
    return serverPasswordText.getText();
  }

  private IServer transcientServer() {
    return ServersManager.getInstance().create(getServerId(), getServerUrl(), getUsername(), getPassword());
  }

}
