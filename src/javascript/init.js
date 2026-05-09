import {registry} from '@jahia/ui-extender';
import register from './WorkflowTasksCleaner/register';
import i18next from 'i18next';

export default function () {
    registry.add('callback', 'workflow-tasks-cleaner', {
        targets: ['jahiaApp-init:50'],
        callback: async () => {
            await i18next.loadNamespaces('workflow-tasks-cleaner', () => {
                console.debug('%c workflow-tasks-cleaner: i18n namespace loaded', 'color: #463CBA');
            });
            register();
            console.debug('%c workflow-tasks-cleaner: activation completed', 'color: #463CBA');
        }
    });
}
