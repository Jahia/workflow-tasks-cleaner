import React, {useEffect, useState} from 'react';
import {useMutation, useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, Input, Tooltip, Typography} from '@jahia/moonstone';
import styles from './WorkflowTasksCleaner.scss';
import {GET_CONFIG, SAVE_CONFIG} from './WorkflowTasksCleaner.gql';

const DEFAULT_CONFIG = {
    cronExpression: '0 30 2 * * ?'
};

export const SchedulerConfig = () => {
    const {t} = useTranslation('workflow-tasks-cleaner');
    const [form, setForm] = useState(DEFAULT_CONFIG);
    const [saveStatus, setSaveStatus] = useState(null);

    const {data, loading} = useQuery(GET_CONFIG, {fetchPolicy: 'network-only'});

    useEffect(() => {
        if (data?.workflowTasksCleanerConfig) {
            setForm({
                cronExpression: data.workflowTasksCleanerConfig.cronExpression
            });
        }
    }, [data]);

    const [saveConfig, {loading: saving}] = useMutation(SAVE_CONFIG);

    const handleChange = (field, value) => {
        setForm(prev => ({...prev, [field]: value}));
    };

    const handleSave = async () => {
        setSaveStatus(null);
        try {
            const result = await saveConfig({
                variables: {
                    cronExpression: form.cronExpression
                }
            });
            setSaveStatus(result.data?.workflowTasksCleanerSaveConfig === true ? 'success' : 'error');
        } catch {
            setSaveStatus('error');
        }
    };

    return (
        <div className={styles.wtc_container}>
            <div className={styles.wtc_header}>
                <h2>{t('label.menu_configuration')}</h2>
            </div>

            <div className={styles.wtc_description}>
                <Typography>{t('label.scheduler.description')}</Typography>
            </div>

            {saveStatus === 'success' && (
                <div className={`${styles.wtc_alert} ${styles['wtc_alert--success']}`}>
                    {t('label.scheduler.saved')}
                </div>
            )}
            {saveStatus === 'error' && (
                <div className={`${styles.wtc_alert} ${styles['wtc_alert--error']}`}>
                    {t('label.error')}
                </div>
            )}

            <div className={styles.wtc_form}>
                <div className={styles.wtc_fieldGroup}>
                    <label className={styles.wtc_label} htmlFor="wtc-cron">
                        {t('label.scheduler.cronExpression')}
                        <Tooltip label={t('label.scheduler.cronExpressionTooltip')}><span className={styles.wtc_tooltip}>ⓘ</span></Tooltip>
                    </label>
                    <Input
                        id="wtc-cron"
                        className={styles.wtc_inputWide}
                        value={loading ? '' : form.cronExpression}
                        isDisabled={loading}
                        onChange={e => handleChange('cronExpression', e.target.value)}
                    />
                </div>
            </div>

            <div className={styles.wtc_restartHint}>
                <Typography variant="caption">{t('label.scheduler.restartHint')}</Typography>
            </div>

            <div className={styles.wtc_actions}>
                <Button
                    label={t('label.scheduler.save')}
                    variant="primary"
                    isDisabled={loading || saving}
                    onClick={handleSave}
                />
            </div>
        </div>
    );
};

export default SchedulerConfig;
