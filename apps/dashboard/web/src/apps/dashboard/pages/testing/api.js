import request from "../../../../util/request"

export default {
    async fetchTestRunTableInfo() {
        const resp = await request({
            url: '/api/fetchTestRunTableInfo',
            method: 'post',
            data: {}
        })
        return resp
    },
    async fetchTestingRunResultSummaries(testingRunHexId) {
        const resp = await request({
            url: '/api/fetchTestingRunResultSummaries',
            method: 'post',
            data: {
                testingRunHexId
            }
        })
        return resp
    },
    async fetchTestingRunResults(testingRunResultSummaryHexId) {
        const resp = await request({
            url: '/api/fetchTestingRunResults',
            method: 'post',
            data: {
                testingRunResultSummaryHexId
            }
        })
        return resp        
    },
    async fetchAllSubCategories () {
        const resp = await request({
            url: 'api/fetchAllSubCategories',
            method: 'post',
            data: {}
        })
        return resp
    },
    async stopTest(testingRunHexId) {
        const resp = await request({
            url: '/api/stopTest',
            method: 'post',
            data: { testingRunHexId }
        })
        return resp        
    },
    async rerunTest(testingRunHexId){
        const resp = await request({
            url: '/api/startTest',
            method: 'post',
            data: { testingRunHexId }
        })
        return resp        
    },
    fetchAffectedEndpoints (issueId) {
        return request({
            url: 'api/fetchAffectedEndpoints',
            method: 'post',
            data: {issueId}
        })
    },
    fetchTestRunResultDetails(testingRunResultHexId) {
        return request({
            url: '/api/fetchTestRunResultDetails',
            method: 'post',
            data: {
                testingRunResultHexId
            }
        })
    },
    fetchIssueFromTestRunResultDetails(testingRunResultHexId) {
        return request({
            url: '/api/fetchIssueFromTestRunResultDetails',
            method: 'post',
            data: {
                testingRunResultHexId
            }
        })
    },
    async fetchCollectionWiseApiEndpoints (apiCollectionId) {
        const resp = await request({
            url: '/api/fetchCollectionWiseApiEndpoints',
            method: 'post',
            data: { apiCollectionId }
        })
        return resp        
    },
    async fetchTestRoles() {
        const resp = await request({
            url: '/api/fetchTestRoles',
            method: 'post',
            data: {}
        })
        return resp
    },
    async addTestRoles (roleName, andConditions, orConditions) {
        const resp = await request({
            url: '/api/addTestRoles',
            method: 'post',
            data: { roleName, andConditions, orConditions }
        })
        return resp        
    },
    async updateTestRoles (roleName, andConditions, orConditions) {
        const resp = await request({
            url: '/api/updateTestRoles',
            method: 'post',
            data: { roleName, andConditions, orConditions }
        })
        return resp        
    },
}