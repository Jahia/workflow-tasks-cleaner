import {DocumentNode} from 'graphql';

describe('Workflow Tasks Cleaner - Configuration UI', () => {
    const adminPath = '/jahia/administration/workflowTasksCleanerConfiguration';

    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const getConfig: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/getConfig.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const saveConfig: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/saveConfig.graphql');

    before(() => {
        cy.login();
    });

    it('shows the Configuration page title', () => {
        cy.login();
        cy.visit(adminPath);
        cy.contains('h2', 'Configuration').should('be.visible');
    });

    it('shows the description block', () => {
        cy.login();
        cy.visit(adminPath);
        cy.get('[class*="wtc_description"]').should('be.visible');
    });

    it('shows the cron expression field', () => {
        cy.login();
        cy.visit(adminPath);
        cy.get('#wtc-cron').should('be.visible');
    });

    it('shows the Save button enabled', () => {
        cy.login();
        cy.visit(adminPath);
        cy.contains('button', 'Save').should('not.be.disabled');
    });

    it('displays a success alert after saving configuration', () => {
        cy.login();
        cy.visit(adminPath);
        cy.contains('button', 'Save').click();
        cy.get('[class*="wtc_alert--success"]', {timeout: 10000}).should('be.visible');
    });

    it('persists cronExpression change via the UI save button', () => {
        const cron = '0 0 4 * * ?';
        cy.login();
        cy.visit(adminPath);

        cy.get('#wtc-cron').clear();
        cy.get('#wtc-cron').type(cron);

        cy.contains('button', 'Save').click();
        cy.get('[class*="wtc_alert--success"]', {timeout: 10000}).should('be.visible');

        cy.apollo({query: getConfig})
            .its('data.workflowTasksCleanerConfig.cronExpression')
            .should('eq', cron);
    });

    after(() => {
        cy.apollo({
            mutation: saveConfig,
            variables: {cronExpression: '0 30 2 * * ?'}
        });
    });
});
