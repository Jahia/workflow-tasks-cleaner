import {DocumentNode} from 'graphql';

/**
 * S25 (G19 / U9, F10) — GENUINE-GAP negative authorization.
 *
 * All four GraphQL fields are @GraphQLRequiresPermission("admin")
 * (WorkflowTasksCleanerQuery.java; WorkflowTasksCleanerMutation.java). A guest / non-admin caller
 * must be rejected on every one of them. This is the explicit contrast with the Karaf path (S19),
 * which has NO equivalent permission gate.
 *
 * Stage-6 finding: `cy.apollo()` authenticates via the shared Apollo client, whose default is Basic
 * `root:<SUPER_USER_PASSWORD>` (see @jahia/cypress apolloClient) — so `cy.logout()` (which only
 * clears the browser session) does NOT make it a guest, and the earlier version false-negatived.
 * The fix installs a genuine GUEST client with empty credentials
 * (`cy.apolloClient({username:'', password:''})` → `Authorization: Basic base64(":")`), which the
 * live Jahia resolves to the anonymous user and denies (verified against the container:
 * `GqlAccessDeniedException` / `data.workflowTasksCleaner == null` for all four fields).
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
        // Install a genuine guest Apollo client (empty credentials -> anonymous user server-side).
        cy.apolloClient({username: '', password: ''});
    });

    afterEach(() => {
        // Restore the default (root) client so nothing leaks into other specs.
        cy.apolloClient();
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
