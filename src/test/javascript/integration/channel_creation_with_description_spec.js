require('../integration_config');

var channelName = utils.randomChannelName();
var channelResource = channelUrl + "/" + channelName;

describe(__filename, function () {

    it('verifies the channel doesn\'t exist yet', function (done) {
        utils.httpGet(channelResource)
            .then(function (response) {
                expect(response.statusCode).toEqual(404);
            })
            .catch(function (error) {
                expect(error).toBeNull();
            })
            .finally(done);
    });

    it('creates a channel with a description', function (done) {
        var url = channelUrl;
        var headers = {'Content-Type': 'application/json'};
        var body = {'name': channelName, 'description': 'describe me'};

        utils.httpPost(url, headers, body)
            .then(function (response) {
                expect(response.statusCode).toEqual(201);
                expect(response.body.description).toEqual('describe me');
            })
            .catch(function (error) {
                expect(error).toBeNull();
            })
            .finally(done);
    });

    it('verifies the channel does exist', function (done) {
        utils.httpGet(channelResource)
            .then(function (response) {
                expect(response.statusCode).toEqual(200);
                expect(response.headers['content-type']).toEqual('application/json');
                expect(response.body.name).toEqual(channelName);
                expect(response.body.description).toEqual('describe me');
            })
            .catch(function (error) {
                expect(error).toBeNull();
            })
            .finally(done);
    });

});
