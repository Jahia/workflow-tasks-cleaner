import {DocumentNode} from 'graphql';

describe('Workflow Tasks Cleaner - Execution UI', () => {
    const adminPath = '/jahia/administration/workflowTasksCleanerExecution';

    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const runClean: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/runClean.graphql');

    before(() => {
        cy.login();
    });

    it('shows the page title', () => {
        cy.login();
        cy.visit(adminPath);
        cy.contains('h2', 'Workflow Tasks Cleaner').should('be.visible');
    });

    it('shows the description block', () => {
        cy.login();
        cy.visit(adminPath);
        cy.get('[class*="wtc_description"]').should('be.visible');
    });

    it('shows the Clean section', () => {
        cy.login();
        cy.visit(adminPath);
        cy.get('[class*="wtc_section"]').first().should('be.visible');
    });

    it('shows the Run clean button', () => {
        cy.login();
        cy.visit(adminPath);
        cy.contains('button', 'Clean').should('be.visible');
    });

    it('shows the workflow list section', () => {
        cy.login();
        cy.visit(adminPath);
        cy.contains('h3', 'Workflow Tasks').should('be.visible');
    });

    it('shows the List workflows button', () => {
        cy.login();
        cy.visit(adminPath);
        cy.contains('button', 'List').should('be.visible');
    });

    it('displays a success alert after clicking Run clean', () => {
        cy.login();
        cy.visit(adminPath);
        cy.contains('button', 'Clean').click();
        cy.get('[class*="wtc_alert--success"]', {timeout: 10000}).should('be.visible');
    });

    it('loads workflow list after clicking List workflows', () => {
        cy.login();
        cy.visit(adminPath);
        cy.contains('button', 'List').click();
        cy.get('[class*="wtc_table"]', {timeout: 10000}).should('be.visible');
    });
});
