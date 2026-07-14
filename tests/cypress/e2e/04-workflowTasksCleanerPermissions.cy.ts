import {DocumentNode} from 'graphql';

/**
 * S25 (G19 / U9, F10) — GENUINE-GAP negative authorization.
 *
 * All four GraphQL fields are @GraphQLRequiresPermission("admin")
 * (WorkflowTasksCleanerQuery.java:32,55; WorkflowTasksCleanerMutation.java:31,52). A guest /
 * non-admin session must be rejected on every one of them. This is the explicit contrast with the
 * Karaf path (S19), which has NO equivalent permission gate.
 *
 * Stage-6 note: uses a guest (logged-out) Apollo client via cy.logout(). If the @jahia/cypress
 * build in the harness exposes a dedicated low-privilege user or guest helper, prefer that; the
 * assertion (errors populated / no data) is what matters, not the exact guest mechanism.
 */
describe('Workflow Tasks Cleaner - Authorization (negative)', () => {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const getConfig: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/getConfig.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const workflowList: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/workflowList.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const runClean: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/runClean.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const saveConfig: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/saveConfig.graphql');

    beforeEach(() => {
        // Ensure no admin session leaks into the guest assertions.
        cy.logout();
    });

    const expectPermissionError = (result: any, field: string) => {
        const denied =
            (result.errors && result.errors.length > 0) ||
            result.data == null ||
            result.data.workflowTasksCleaner == null ||
            result.data.workflowTasksCleaner[field] == null;
        expect(denied, `guest must be denied on ${field}`).to.be.true;
    };

    it('denies config to a non-admin', () => {
        cy.apollo({query: getConfig, errorPolicy: 'all'}).then((r: any) => expectPermissionError(r, 'config'));
    });

    it('denies workflowList to a non-admin', () => {
        cy.apollo({query: workflowList, errorPolicy: 'all'}).then((r: any) => expectPermissionError(r, 'workflowList'));
    });

    it('denies runClean to a non-admin', () => {
        cy.apollo({mutation: runClean, errorPolicy: 'all'}).then((r: any) => expectPermissionError(r, 'runClean'));
    });

    it('denies saveConfig to a non-admin', () => {
        cy.apollo({
            mutation: saveConfig,
            variables: {cronExpression: '0 0 3 * * ?'},
            errorPolicy: 'all'
        }).then((r: any) => expectPermissionError(r, 'saveConfig'));
    });
});
