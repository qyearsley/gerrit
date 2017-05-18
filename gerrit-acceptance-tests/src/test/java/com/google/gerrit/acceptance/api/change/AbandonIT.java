// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.ChangeControl;
import java.util.List;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

public class AbandonIT extends AbstractDaemonTest {
  @Test
  public void abandon() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.NEW);
    gApi.changes().id(changeId).abandon();
    ChangeInfo info = get(changeId);
    assertThat(info.status).isEqualTo(ChangeStatus.ABANDONED);
    assertThat(Iterables.getLast(info.messages).message.toLowerCase()).contains("abandoned");

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("change is abandoned");
    gApi.changes().id(changeId).abandon();
  }

  @Test
  public void batchAbandon() throws Exception {
    CurrentUser user = atrScope.get().getUser();
    PushOneCommit.Result a = createChange();
    List<ChangeControl> controlA = changeFinder.find(a.getChangeId(), user);
    assertThat(controlA).hasSize(1);
    PushOneCommit.Result b = createChange();
    List<ChangeControl> controlB = changeFinder.find(b.getChangeId(), user);
    assertThat(controlB).hasSize(1);
    List<ChangeControl> list = ImmutableList.of(controlA.get(0), controlB.get(0));
    changeAbandoner.batchAbandon(
        batchUpdateFactory, controlA.get(0).getProject().getNameKey(), user, list, "deadbeef");

    ChangeInfo info = get(a.getChangeId());
    assertThat(info.status).isEqualTo(ChangeStatus.ABANDONED);
    assertThat(Iterables.getLast(info.messages).message.toLowerCase()).contains("abandoned");
    assertThat(Iterables.getLast(info.messages).message.toLowerCase()).contains("deadbeef");

    info = get(b.getChangeId());
    assertThat(info.status).isEqualTo(ChangeStatus.ABANDONED);
    assertThat(Iterables.getLast(info.messages).message.toLowerCase()).contains("abandoned");
    assertThat(Iterables.getLast(info.messages).message.toLowerCase()).contains("deadbeef");
  }

  @Test
  public void batchAbandonChangeProject() throws Exception {
    String project1Name = name("Project1");
    String project2Name = name("Project2");
    gApi.projects().create(project1Name);
    gApi.projects().create(project2Name);
    TestRepository<InMemoryRepository> project1 = cloneProject(new Project.NameKey(project1Name));
    TestRepository<InMemoryRepository> project2 = cloneProject(new Project.NameKey(project2Name));

    CurrentUser user = atrScope.get().getUser();
    PushOneCommit.Result a = createChange(project1, "master", "x", "x", "x", "");
    List<ChangeControl> controlA = changeFinder.find(a.getChangeId(), user);
    assertThat(controlA).hasSize(1);
    PushOneCommit.Result b = createChange(project2, "master", "x", "x", "x", "");
    List<ChangeControl> controlB = changeFinder.find(b.getChangeId(), user);
    assertThat(controlB).hasSize(1);
    List<ChangeControl> list = ImmutableList.of(controlA.get(0), controlB.get(0));
    exception.expect(ResourceConflictException.class);
    exception.expectMessage(
        String.format("Project name \"%s\" doesn't match \"%s\"", project2Name, project1Name));
    changeAbandoner.batchAbandon(batchUpdateFactory, new Project.NameKey(project1Name), user, list);
  }

  @Test
  public void abandonDraft() throws Exception {
    PushOneCommit.Result r = createDraftChange();
    String changeId = r.getChangeId();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.DRAFT);

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("draft changes cannot be abandoned");
    gApi.changes().id(changeId).abandon();
  }

  @Test
  public void abandonNotAllowedWithoutPermission() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.NEW);
    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("abandon not permitted");
    gApi.changes().id(changeId).abandon();
  }

  @Test
  public void abandonAndRestoreAllowedWithPermission() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.NEW);
    grant(project, "refs/heads/master", Permission.ABANDON, false, REGISTERED_USERS);
    setApiUser(user);
    gApi.changes().id(changeId).abandon();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.ABANDONED);
    gApi.changes().id(changeId).restore();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.NEW);
  }

  @Test
  public void restore() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.NEW);
    gApi.changes().id(changeId).abandon();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.ABANDONED);

    gApi.changes().id(changeId).restore();
    ChangeInfo info = get(changeId);
    assertThat(info.status).isEqualTo(ChangeStatus.NEW);
    assertThat(Iterables.getLast(info.messages).message.toLowerCase()).contains("restored");

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("change is new");
    gApi.changes().id(changeId).restore();
  }

  @Test
  public void restoreNotAllowedWithoutPermission() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.NEW);
    gApi.changes().id(changeId).abandon();
    setApiUser(user);
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.ABANDONED);
    exception.expect(AuthException.class);
    exception.expectMessage("restore not permitted");
    gApi.changes().id(changeId).restore();
  }
}
