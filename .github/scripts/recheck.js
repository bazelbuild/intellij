module.exports = async (github, context, core) => {
  // check the comment body for '/recheck'
  const commentBody = context.payload.comment.body.trim();
  if (commentBody !== '/recheck') {
    console.log('Comment is not /recheck. Skipping.');
    return;
  }

  // check permissions of the comment author
  const association = context.payload.comment.author_association;
  const allowed = ['OWNER', 'MEMBER', 'COLLABORATOR'];
  if (!allowed.includes(association)) {
     console.log(`User association ${association} is not allowed. Skipping.`);
     return;
  }

  const { owner, repo } = context.repo;
  const prNumber = context.issue.number;

  // get the Commenter's details to spoof the author
  const commenter = context.payload.comment.user;
  // construct the standard GitHub noreply email: ID+Login@users.noreply.github.com
  const authorName = commenter.login;
  const authorEmail = `${commenter.id}+${commenter.login}@users.noreply.github.com`;

  // get the current PR
  const { data: pr } = await github.rest.pulls.get({
    owner,
    repo,
    pull_number: prNumber,
  });

  if (!pr.head.repo) {
    console.log("The source repository (fork) seems to be deleted. Cannot commit.");
    return;
  }

  const forkOwner = pr.head.repo.owner.login;
  const forkRepo = pr.head.repo.name;
  const forkRef = pr.head.ref;

  console.log(`Targeting repo: ${forkOwner}/${forkRepo} on branch: ${forkRef}`);

  // get the current commit
  const { data: currentCommit } = await github.rest.git.getCommit({
    owner: forkOwner,
    repo: forkRepo,
    commit_sha: pr.head.sha,
  });

  // Create the empty commit with the commenter as the Author
  const { data: newCommit } = await github.rest.git.createCommit({
    owner: forkOwner,
    repo: forkRepo,
    message: `Triggering CI (empty commit)\n\nAutomatically created on behalf of @${authorName}`,
    tree: currentCommit.tree.sha,
    parents: [pr.head.sha],
    author: {
      name: authorName,
      email: authorEmail,
      date: new Date().toISOString() // timestamp is required when manually setting author
    }
  });

  // update the branch reference
  await github.rest.git.updateRef({
    owner: forkOwner,
    repo: forkRepo,
    ref: `heads/${pr.head.ref}`,
    sha: newCommit.sha,
  });

  // add a reaction to the comment
  await github.rest.reactions.createForIssueComment({
    owner,
    repo,
    comment_id: context.payload.comment.id,
    content: 'eyes',
  });
}