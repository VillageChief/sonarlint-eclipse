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

import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;

public final class SonarLintAnalyzerLogOutput implements LogOutput {

  @Override
  public void log(String msg, Level level) {
    switch (level) {
      case TRACE:
      case DEBUG:
        SonarLintCorePlugin.getDefault().analyzerDebug(msg);
        break;
      case INFO:
      case WARN:
        SonarLintCorePlugin.getDefault().analyzerInfo(msg);
        break;
      case ERROR:
        SonarLintCorePlugin.getDefault().analyzerError(msg);
        break;
      default:
        SonarLintCorePlugin.getDefault().analyzerInfo(msg);
    }

  }
}
