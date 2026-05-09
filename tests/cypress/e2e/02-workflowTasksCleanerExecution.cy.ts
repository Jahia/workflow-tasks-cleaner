import {DocumentNode} from 'graphql';

describe('Workflow Tasks Cleaner - Execution UI', () => {
    const adminPath = '/jahia/administration/workflowTasksCleanerExecution';

    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const addPage: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/addPage.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const publishNode: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/publishNode.graphql');

    before(() => {
        cy.login();

        // Add a page to digitall and publish it to trigger a workflow
        cy.apollo({
            mutation: addPage,
            variables: {parentPath: '/sites/digitall/home', name: 'test-page', template: 'simple'}
        }).then(() => {
            // eslint-disable-next-line cypress/no-unnecessary-waiting
            cy.wait(2000);
            cy.apollo({
                mutation: publishNode,
                variables: {path: '/sites/digitall/home/test-page'}
            });
        });
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
        cy.contains('button', 'Run cleaner').should('be.visible');
    });

    it('shows the workflow list section', () => {
        cy.login();
        cy.visit(adminPath);
        cy.contains('h3', 'Workflow instances').should('be.visible');
    });

    it('shows the List workflows button', () => {
        cy.login();
        cy.visit(adminPath);
        cy.contains('button', 'List').should('be.visible');
    });

    it('displays a success alert after clicking Run clean', () => {
        cy.login();
        cy.visit(adminPath);
        cy.contains('button', 'Run cleaner').click();
        cy.get('[class*="wtc_alert--success"]', {timeout: 10000}).should('be.visible');
    });

    it('loads workflow list after clicking List workflows', () => {
        cy.login();
        cy.visit(adminPath);
        cy.contains('button', 'List workflows').click();
        cy.get('[class*="wtc_loading"]', {timeout: 10000}).should('not.exist');

        // Either table with workflows or no-workflows message is shown
        cy.get('body').then($body => {
            if ($body.find('[class*="wtc_table"]').length > 0) {
                cy.get('[class*="wtc_table"] tbody tr').should('have.length.at.least', 1);
            } else {
                cy.contains('No workflow instances found.').should('be.visible');
            }
        });
    });
});
