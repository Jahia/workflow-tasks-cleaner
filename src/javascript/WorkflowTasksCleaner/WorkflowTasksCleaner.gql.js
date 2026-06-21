import {gql} from '@apollo/client';

export const GET_CONFIG = gql`
    query WorkflowTasksCleanerConfig {
        workflowTasksCleaner {
            config {
                cronExpression
            }
        }
    }
`;

export const SAVE_CONFIG = gql`
    mutation WorkflowTasksCleanerSaveConfig($cronExpression: String) {
        workflowTasksCleaner {
            saveConfig(cronExpression: $cronExpression)
        }
    }
`;

export const RUN_CLEAN = gql`
    mutation WorkflowTasksCleanerRunClean {
        workflowTasksCleaner {
            runClean
        }
    }
`;

export const WORKFLOW_LIST = gql`
    query WorkflowTasksCleanerWorkflowList {
        workflowTasksCleaner {
            workflowList {
                id
                name
                type
                title
            }
        }
    }
`;
