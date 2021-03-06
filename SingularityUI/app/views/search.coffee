View = require './view'

TasksSearch = require '../collections/TasksSearch'
RequestsSearch = require '../collections/RequestsSearch'

class SearchView extends View

    template: require './templates/search'
    templateResults: require './templates/searchResults'

    render: =>
        @$el.html @template
        @setUpSearchEvents()

    renderResults: ->
        context =
            tasksResults: _.first(_.pluck(@tasksResults.models, 'attributes'), 10)
            requestsResults: _.first(_.pluck(@requestsResults.models, 'attributes'), 10)

        @$el.find('.results').html @templateResults context

        utils.setupSortableTables()

    setupEvents: ->
        @$el.find('.view-json').unbind('click').click (event) ->
            utils.viewJSON 'task', $(event.target).data('task-id')

    setUpSearchEvents: ->
        $search = @$el.find('input[type="search"]')
        $search.focus() if $(window).width() > 568

        lastText = _.trim $search.val()

        $search.on 'change keypress paste focus textInput input click keydown', _.debounce =>
            text = _.trim $search.val()

            if text isnt lastText and text.length
                if @lastXhrTasks?
                   @lastXhrTasks.abort()
                   @lastXhrRequests.abort()

                lastText = text

                @tasksResults = new TasksSearch [], query: text
                @lastXhrTasks = @tasksResults.fetch()

                @requestsResults = new RequestsSearch [], query: text
                @lastXhrRequests = @requestsResults.fetch()

                $.when(@lastXhrTasks, @lastXhrRequests).done => @renderResults()

        , 35

module.exports = SearchView