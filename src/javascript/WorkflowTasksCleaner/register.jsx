import {registry} from '@jahia/ui-extender';
import {WorkflowTasksCleanerAdmin} from './WorkflowTasksCleaner';
import {SchedulerConfig} from './SchedulerConfig';
import React from 'react';

export default () => {
    console.debug('%c workflow-tasks-cleaner: activation in progress', 'color: #463CBA');
    registry.add('adminRoute', 'workflowTasksCleaner', {
        targets: ['administration-server-systemHealth:999'],
        requiredPermission: 'admin',
        label: 'workflow-tasks-cleaner:label.menu_entry',
        isSelectable: false
    });
    registry.add('adminRoute', 'workflowTasksCleanerExecution', {
        targets: ['administration-server-workflowTasksCleaner:1'],
        requiredPermission: 'admin',
        label: 'workflow-tasks-cleaner:label.menu_execution',
        isSelectable: true,
        render: () => React.createElement(WorkflowTasksCleanerAdmin)
    });
    registry.add('adminRoute', 'workflowTasksCleanerConfiguration', {
        targets: ['administration-server-workflowTasksCleaner:2'],
        requiredPermission: 'admin',
        label: 'workflow-tasks-cleaner:label.menu_configuration',
        isSelectable: true,
        render: () => React.createElement(SchedulerConfig)
    });
};
