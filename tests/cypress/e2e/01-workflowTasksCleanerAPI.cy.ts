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
    });
});
