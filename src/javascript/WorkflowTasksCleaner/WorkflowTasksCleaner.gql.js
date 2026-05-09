import {gql} from '@apollo/client';

export const GET_CONFIG = gql`
    query WorkflowTasksCleanerConfig {
        workflowTasksCleanerConfig {
            cronExpression
        }
    }
`;

export const SAVE_CONFIG = gql`
    mutation WorkflowTasksCleanerSaveConfig($cronExpression: String) {
        workflowTasksCleanerSaveConfig(cronExpression: $cronExpression)
    }
`;

export const RUN_CLEAN = gql`
    mutation WorkflowTasksCleanerRunClean {
        workflowTasksCleanerRunClean
    }
`;

export const WORKFLOW_LIST = gql`
    query WorkflowTasksCleanerWorkflowList {
        workflowTasksCleanerWorkflowList {
            id
            name
            type
            title
        }
    }
`;
