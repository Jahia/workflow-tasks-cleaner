import {DocumentNode} from 'graphql';

/**
 * S14 (G1 / U2) — SAFETY HEADLINE end-to-end: runClean aborts an ACTIVE workflow whose target node
 * was deleted. This is the *real* runtime proof that the JUnit characterization (S13) can only
 * simulate, and it replaces the existing worthless "runClean returns true" assertion (which is
 * satisfied before the async worker runs).
 *
 * ============================ WHY THIS IS `describe.skip` ============================
 * Stage-4 analysis (04-gaps.md §4) found this NOT reliably runnable AS ORIGINALLY SPECIFIED, and it
 * cannot be de-risked without a live container (Cypress runs are deferred to Stage 6). Three blockers
 * must be resolved by a Stage-6 SPIKE before this is un-skipped:
 *
 *   1. publishNode.graphql publishes DIRECTLY as admin (no jBPM workflow is started). A publication
 *      *workflow* only starts via request-publication / startWorkflow when the actor lacks direct
 *      publish rights. => seeding must use an explicit request-publication flow OR a purpose-created
 *      restricted editor, NOT admin direct-publish.
 *   2. cleanGhostWorkflows enumerates tasks for the ROOT user (CleanCommand.java:74/lookupRootJahiaUser).
 *      If the pending task is not visible to root, the ghost loop never processes it.
 *   3. The abort only fires when the workflow carries a `nodeIds` List variable
 *      (CleanCommand.java:89-96). Whether Jahia's publication workflow stores that exact key/type is
 *      UNVERIFIED. If it does not, the defensive skip means no abort — and S14 must be WRITTEN-BUT-SKIPPED
 *      with that exact finding recorded for Stage 7 (do NOT fake a pass).
 *
 * The body below is the intended spike-then-test shape: it seeds, then ASSERTS THE WORKFLOW IS PRESENT
 * BEFORE deleting the node (failing loudly otherwise, so the test can never silently pass on an empty
 * set), deletes the node, runs clean, and polls workflowList to observe the abort. Stage 6 must:
 *   (a) replace the seeding with a flow that provably starts an active publication workflow carrying
 *       nodeIds visible to root;
 *   (b) confirm the spike (workflow present + nodeIds var) — then remove `.skip`;
 *   (c) if the spike disproves reachability, keep it skipped and record the finding for Stage 7.
 * =====================================================================================
 */
// eslint-disable-next-line mocha/no-skipped-tests
describe.skip('Workflow Tasks Cleaner - Ghost abort e2e (SAFETY, spike-then-test)', () => {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const addPage: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/addPage.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const publishNode: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/publishNode.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const deleteNode: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/deleteNode.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const runClean: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/runClean.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const workflowList: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/workflowList.graphql');

    const pagePath = '/sites/digitall/home/ghost-abort-test';

    afterEach(() => {
        // Destructive test: best-effort cleanup regardless of outcome.
        cy.login();
        cy.apollo({mutation: deleteNode, variables: {pathOrId: pagePath}, errorPolicy: 'all'});
    });

    it('aborts an active publication workflow whose target node was deleted', () => {
        cy.login();

        // Arrange: seed a workflow. STAGE 6 must swap this for a flow that actually starts a
        // request-publication workflow (see blockers #1/#2 above).
        cy.apollo({mutation: addPage, variables: {parentPath: '/sites/digitall/home', name: 'ghost-abort-test', template: 'simple'}});
        // eslint-disable-next-line cypress/no-unnecessary-waiting
        cy.wait(2000);
        cy.apollo({mutation: publishNode, variables: {path: pagePath}});

        // FAIL LOUDLY if no workflow was actually seeded — the test must never pass on an empty set.
        cy.apollo({query: workflowList, fetchPolicy: 'no-cache'})
            .its('data.workflowTasksCleaner.workflowList')
            .should((wfs: any[]) => {
                expect(wfs.length, 'a workflow must exist BEFORE deleting the node (spike precondition)')
                    .to.be.greaterThan(0);
            });

        // Capture the seeded workflow ids to observe their disappearance.
        cy.apollo({query: workflowList, fetchPolicy: 'no-cache'})
            .its('data.workflowTasksCleaner.workflowList')
            .then((before: any[]) => {
                const seededIds = before.map(w => w.id);

                // Act: delete the target node so its nodeIds no longer resolve, then run clean.
                cy.apollo({mutation: deleteNode, variables: {pathOrId: pagePath}});
                cy.apollo({mutation: runClean}).its('data.workflowTasksCleaner.runClean').should('eq', true);

                // Assert (current buggy behavior — characterization): poll until the seeded workflow
                // disappears (its process was abortProcess-ed) even though it was active.
                const pollGone = (attempt = 0) => {
                    cy.apollo({query: workflowList, fetchPolicy: 'no-cache'})
                        .its('data.workflowTasksCleaner.workflowList')
                        .then((now: any[]) => {
                            const stillThere = now.some(w => seededIds.includes(w.id));
                            if (!stillThere) {
                                expect(stillThere, 'seeded active workflow was aborted by runClean').to.be.false;
                            } else if (attempt < 15) {
                                // eslint-disable-next-line cypress/no-unnecessary-waiting
                                cy.wait(1000).then(() => pollGone(attempt + 1));
                            } else {
                                throw new Error('Ghost abort not observed within timeout — re-run the Stage-6 spike (blockers #1-#3).');
                            }
                        });
                };
                pollGone();
            });
    });
});
