const {
    closeServer,
    createChannel,
    deleteWebhook,
    fromObjectPath,
    getProp,
    hubClientPostTestItem,
    hubClientDelete,
    itSleeps,
    putWebhook,
    randomChannelName,
    randomString,
    startServer,
    waitForCondition,
} = require('../lib/helpers');
const {
    getCallBackDomain,
    getCallBackPort,
    getChannelUrl,
} = require('../lib/config');

const channelUrl = getChannelUrl();
const callbackDomain = getCallBackDomain();
const port = getCallBackPort();
const channelName = randomChannelName();
const webhookName = randomChannelName();
const channelResource = `${channelUrl}/${channelName}`;
const callbackPath = `/${randomString(5)}`;
const callbackUrl = `${callbackDomain}:${port}${callbackPath}`;
let createdChannel = false;
const postedItems = [];
let callbackServer = null;
const callbackItems = [];
/**
 * This should:
 *
 * 1 - create a channel
 * 2 - add items to the channel
 * 2 - create a webhook on that channel with startItem=previous
 * 3 - start a server at the endpoint
 * 4 - post item into the channel
 * 5 - verify that the item are returned within delta time, including the second item posted in 2.
 */
describe(__filename, function () {
    beforeAll(async () => {
        const channel = await createChannel(channelName, false, __filename);
        if (getProp('statusCode', channel) === 201) {
            createdChannel = true;
            console.log(`created channel for ${__filename}`);
        }
    });

    it('waits 1000 ms', async () => {
        await itSleeps(1000);
    });

    function addPostedItem (value) {
        postedItems.push(fromObjectPath(['body', '_links', 'self', 'href'], value));
        console.log('postedItems', postedItems);
    }

    it(`posts initial items  ${channelResource}`, async () => {
        if (!createdChannel) return fail('channel not created in before block');
        await hubClientPostTestItem(channelResource);
        const response = await hubClientPostTestItem(channelResource);
        addPostedItem(response);
    });

    it('waits 6000 ms', async () => {
        await itSleeps(6000);
    });

    it('creates the webhook', async () => {
        const webhookConfig = {
            callbackUrl: callbackUrl,
            channelUrl: channelResource,
            startItem: 'previous',
        };
        const response = await putWebhook(webhookName, webhookConfig, 201, __filename);
        expect(getProp('statusCode', response)).toEqual(201);
    });

    it('starts a callback server', async () => {
        if (!createdChannel) return fail('channel not created in before block');
        const callback = (string) => {
            console.log(`called webhook ${webhookName} ${string}`);
            callbackItems.push(string);
        };
        callbackServer = await startServer(port, callback, callbackPath);
    });

    it('inserts items', async () => {
        if (!createdChannel) return fail('channel not created in before block');
        const response0 = await hubClientPostTestItem(channelResource);
        const response1 = await hubClientPostTestItem(channelResource);
        const response2 = await hubClientPostTestItem(channelResource);
        const response3 = await hubClientPostTestItem(channelResource);
        [response0, response1, response2, response3]
            .forEach(res => addPostedItem(res));
        const condition = () => (callbackItems.length === postedItems.length);
        await waitForCondition(condition);
    });

    it('closes the first callback server', async () => {
        if (!createdChannel) return fail('channel not created in before block');
        expect(callbackServer).toBeDefined();
        await closeServer(callbackServer);
    });

    it('verifies we got what we expected through the callback', function () {
        if (!createdChannel) return fail('channel not created in before block');
        expect(callbackItems.length).toBe(5);
        expect(postedItems.length).toBe(5);
        for (let i = 0; i < callbackItems.length; i++) {
            expect(callbackItems[i]).toBe(postedItems[i]);
        }
    });

    it('deletes the webhook', async () => {
        const response = await deleteWebhook(webhookName);
        expect(getProp('statusCode', response)).toBe(202);
    });

    afterAll(async () => {
        await hubClientDelete(channelResource);
    });
});
