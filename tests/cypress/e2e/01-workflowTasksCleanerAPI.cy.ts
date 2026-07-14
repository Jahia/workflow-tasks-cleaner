import {DocumentNode} from 'graphql';

describe('Workflow Tasks Cleaner - GraphQL API', () => {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const getConfig: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/getConfig.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const saveConfig: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/saveConfig.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const runClean: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/runClean.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const workflowList: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/workflowList.graphql');
    // Negative fixtures — real-API-shape (D1/D2): the documented fields/args below are fiction.
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const getConfigFictionalFields: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/getConfigFictionalFields.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const workflowListTaskStateArg: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/workflowListTaskStateArg.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const workflowListFictionalFields: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/workflowListFictionalFields.graphql');

    before(() => {
        cy.login();
    });

    // --- workflowTasksCleanerConfig ---

    describe('workflowTasksCleanerConfig', () => {
        it('returns all config fields', () => {
            cy.apollo({query: getConfig})
                .its('data.workflowTasksCleaner.config')
                .should(config => {
                    expect(config).to.have.property('cronExpression');
                });
        });

        it('returns cronExpression as a non-empty string', () => {
            cy.apollo({query: getConfig})
                .its('data.workflowTasksCleaner.config.cronExpression')
                .should('be.a', 'string')
                .and('not.be.empty');
        });
    });

    // --- workflowTasksCleanerSaveConfig ---

    describe('workflowTasksCleanerSaveConfig', () => {
        it('saves cronExpression and reads it back', () => {
            const cron = '0 0 3 * * ?';
            cy.apollo({
                mutation: saveConfig,
                variables: {cronExpression: cron}
            })
                .its('data.workflowTasksCleaner.saveConfig')
                .should('eq', true);

            cy.apollo({query: getConfig})
                .its('data.workflowTasksCleaner.config.cronExpression')
                .should('eq', cron);
        });

        it('returns true when saving valid config', () => {
            cy.apollo({
                mutation: saveConfig,
                variables: {cronExpression: '0 30 2 * * ?'}
            })
                .its('data.workflowTasksCleaner.saveConfig')
                .should('eq', true);
        });
    });

    // --- workflowTasksCleanerRunClean ---

    describe('workflowTasksCleanerRunClean', () => {
        it('triggers cleanup and returns true', () => {
            cy.apollo({mutation: runClean})
                .its('data.workflowTasksCleaner.runClean')
                .should('eq', true);
        });
    });

    // --- workflowTasksCleanerWorkflowList ---

    describe('workflowTasksCleanerWorkflowList', () => {
        it('returns a list (may be empty)', () => {
            cy.apollo({query: workflowList})
                .its('data.workflowTasksCleaner.workflowList')
                .should('be.an', 'array');
        });

        it('returns workflow objects with expected fields', () => {
            cy.apollo({query: workflowList})
                .its('data.workflowTasksCleaner.workflowList')
                .should(wfs => {
                    if (wfs.length > 0) {
                        expect(wfs[0]).to.have.property('id');
                        expect(wfs[0]).to.have.property('name');
                        expect(wfs[0]).to.have.property('type');
                        expect(wfs[0]).to.have.property('title');
                    }
                });
        });

        it('returns workflow objects with EXACTLY id/name/type/title and no others', () => {
            cy.apollo({query: workflowList})
                .its('data.workflowTasksCleaner.workflowList')
                .should(wfs => {
                    if (wfs.length > 0) {
                        // strip Apollo's __typename before comparing the field set
                        const keys = Object.keys(wfs[0]).filter(k => k !== '__typename');
                        expect(keys.sort()).to.deep.eq(['id', 'name', 'title', 'type']);
                    }
                });
        });
    });

    // --- Real-API-shape: documented fields/args are FICTION (S22/S23, D1/D2) ---

    describe('real API shape (documented counters/args do not exist)', () => {
        it('S22: config query for isRunning/tasksScanned/tasksDeleted/tasksFixed fails validation', () => {
            // errorPolicy 'all' so Apollo returns the GraphQL validation errors instead of throwing.
            cy.apollo({query: getConfigFictionalFields, errorPolicy: 'all'}).then((result: any) => {
                expect(result.errors, 'unknown config fields must be rejected by the schema').to.exist;
                expect(result.errors.length).to.be.greaterThan(0);
            });
        });

        it('S23: workflowList(taskState: ESCALATED) fails validation — no such argument/enum', () => {
            cy.apollo({query: workflowListTaskStateArg, errorPolicy: 'all'}).then((result: any) => {
                expect(result.errors, 'taskState argument must not exist on workflowList').to.exist;
                expect(result.errors.length).to.be.greaterThan(0);
            });
        });

        it('S23: workflowList { processId assignee dueDate } fails validation — fields do not exist', () => {
            cy.apollo({query: workflowListFictionalFields, errorPolicy: 'all'}).then((result: any) => {
                expect(result.errors, 'per-task fields processId/assignee/dueDate must not exist').to.exist;
                expect(result.errors.length).to.be.greaterThan(0);
            });
        });
    });
});
