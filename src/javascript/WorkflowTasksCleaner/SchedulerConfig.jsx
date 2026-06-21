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

    useEffect(() => {
        document.title = `${t('label.scheduler.configTitle')} — Jahia Administration`;
    }, [t]);

    const {data, loading} = useQuery(GET_CONFIG, {fetchPolicy: 'network-only'});

    useEffect(() => {
        if (data?.workflowTasksCleaner?.config) {
            setForm({
                cronExpression: data.workflowTasksCleaner.config.cronExpression
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
            setSaveStatus(result.data?.workflowTasksCleaner?.saveConfig === true ? 'success' : 'error');
        } catch {
            setSaveStatus('error');
        }
    };

    return (
        <div className={styles.wtc_container}>
            {/* Two fixed-role live regions — AT caches role at registration; dynamic mutation is ignored */}
            <div role="status" aria-live="polite" aria-atomic="true" className={styles.wtc_sr_only}>
                {saveStatus === 'success' ? t('label.scheduler.saved') : ''}
            </div>
            <div role="alert" aria-live="assertive" aria-atomic="true" className={styles.wtc_sr_only}>
                {saveStatus === 'error' ? t('label.error') : ''}
            </div>

            <div className={styles.wtc_header}>
                <h2>{t('label.scheduler.configTitle')}</h2>
            </div>

            <div className={styles.wtc_description}>
                <Typography>{t('label.scheduler.description')}</Typography>
            </div>

            {saveStatus === 'success' && (
                <div aria-hidden="true" className={`${styles.wtc_alert} ${styles['wtc_alert--success']}`}>
                    <span className={styles.wtc_alertIcon}>✓</span> {t('label.scheduler.saved')}
                </div>
            )}
            {saveStatus === 'error' && (
                <div aria-hidden="true" className={`${styles.wtc_alert} ${styles['wtc_alert--error']}`}>
                    <span className={styles.wtc_alertIcon}>✕</span> {t('label.error')}
                </div>
            )}

            <div className={styles.wtc_form}>
                <div className={styles.wtc_fieldGroup}>
                    {/* Tooltip trigger moved outside <label> — interactive elements must not be children of <label> */}
                    <div className={styles.wtc_label}>
                        <label htmlFor="wtc-cron">
                            {t('label.scheduler.cronExpression')}
                        </label>
                        <Tooltip label={t('label.scheduler.cronExpressionTooltip')}>
                            <button
                                type="button"
                                aria-label={t('label.scheduler.cronExpressionInfo')}
                                className={styles.wtc_tooltip}
                            >
                                ⓘ
                            </button>
                        </Tooltip>
                    </div>
                    <Input
                        id="wtc-cron"
                        aria-describedby={`wtc-cron-desc${saveStatus === 'error' ? ' wtc-cron-err' : ''}`}
                        aria-invalid={saveStatus === 'error' ? 'true' : undefined}
                        aria-required="true"
                        required
                        className={styles.wtc_inputWide}
                        value={loading ? '' : form.cronExpression}
                        isDisabled={loading}
                        autoComplete="off"
                        onChange={e => handleChange('cronExpression', e.target.value)}
                    />
                    <span id="wtc-cron-desc" className={styles.wtc_sr_only}>
                        {t('label.scheduler.cronExpressionTooltip')}
                    </span>
                    <span id="wtc-cron-err" className={styles.wtc_sr_only}>
                        {saveStatus === 'error' ? t('label.error') : ''}
                    </span>
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
