#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Handler::Misc v1.3.0;

use 5.012;
use warnings;
use utf8;

use Handler;

use Time::HiRes qw(tv_interval gettimeofday);
use FindBin;

#initialises the request handlers
sub init {
	Server::register_handler(handler(url_main()),\&index);
	Server::register_handler(handler(url_view('','')),\&view);
	Server::register_handler(handler(url_front('')),\&front);
	Server::register_handler(handler(url_search('')),\&search);
	Server::register_handler(handler(url_tools()),\&tools);
	Server::register_handler('b',\&blob);
	Server::register_handler('css',\&css);
}

#sends the default css file
sub css {
	my ($c,$r) = @_;
	$c->send_file_response($FindBin::Bin.'/style.css');
}

#$connection, $request
#handles index requests
sub index {
	my ($c,$r,$debug) = @_;
	Log->trace('handle index');
	my $html = html_header('index','index');
	$html .= cgi->start_fieldset({-class=>'info'});
	$html .= cgi->legend('Search');
	$html .= form_search();
	$html .= cgi->end_fieldset();
	my $bm = UserPrefs->section('bookmark');
	#do some name mapping for performance or peace of mind at least
	my %bmd = map {$_ =>  Cores::known($_) ? Cores::name($_) : $_} grep {$bm->get($_)} $bm->list();
	my %last = map {$_ => Collection->get($_)->last()//0} keys %bmd;
	%bmd = map {$_ => $bmd{$_} . (($last{$_} - $bm->get($_)) ? ( ' (' . ($last{$_} - $bm->get($_)) . ') ') : '')} keys %bmd;
	my %new = map {$_ => $bmd{$_}} grep { $bm->get($_) < $last{$_} } keys %bmd;
	my $mt = Maintenance->new();
	my %times;
	if ($debug) {
		for my $bm (keys %bmd) {
			my ($t, $n) = split(':', $mt->cfg('update')->{$bm}//'');
			$times{$bm} = [time - $t, $n];
		}
	}

	my $pretty_seconds = sub {
		my $t = shift;
		my $s = $t % 60;
		$t /= 60;
		my $m = int($t) % 60;
		$t /= 60;
		my $h = int($t) % 24;
		$t /= 24;
		my $d = int($t);
		return "(${d}d ${h}h)";
	};

	$html .= html_group('New Pages' ,map {[$_ , $new{$_} . ($debug?(' ' . $pretty_seconds->($times{$_}->[0]) . ' ' . $pretty_seconds->($times{$_}->[1])):''), $bm->get($_) - $last{$_} ]} keys %new);
	$html .= html_group('Bookmarks' ,map {[$_ ,
		($debug
			? ($bmd{$_} . (' ' . $pretty_seconds->($times{$_}->[0]) . ' ' . $pretty_seconds->($times{$_}->[1])), $times{$_}->[0])
			: ($bmd{$_})x2
		)]} grep {!$new{$_}} keys %bmd);
	$html .= html_core_status();
	$html .= cgi->end_html();
	Server::send_response($c,$html);
	return 'index';
}

#$connection, $request, $id, 4pos
#handles view requests
sub view {
	my ($c,$r,$id,$pos) = @_;
	Log->trace('handle collection');
	my $col = Collection->get($id);
	$pos =~ s/(\d+)\:(\d+)/$1 . '..' . ($1 + $2 - 1)/ge;
	$pos =~ s/[^\d,\.]//g;
	my @pos = eval($pos);
	my @ent = map { $col->fetch($_) } @pos ;
	unless ($ent[0]) {
		my $last = $col->last();
		if ($last) {
			Log->debug("$pos not found redirect to last $last");
			$c->send_redirect( absolute(url_view($id,$last)), 303 );
		}
		else {
			Log->debug("$pos not found redirect to front");
			$c->send_redirect( absolute(url_front($id)), 303 );
		}
		return "view redirect";
	}
	my $next_pos = join ',', map { $_ + @pos } @pos;
	my $prev_pos = join ',', grep {$_ > 0} map { $_ - @pos } @pos;
	my $html = html_header('view',$pos);
	$html .= cgi->start_div({-class=>'content'});
	$html .= link_view($id,$next_pos,$_->html()) for grep {$_} @ent;
	$html .= cgi->end_div();
	$html .= cgi->start_div({-class=>'navigation'});
		$html .= link_view($id,$prev_pos,'prev') if ($prev_pos);
		$html .= ' ';
		$html .= link_front($id,'front');
		$html .= ' ';
			$html .= cgi->start_form(-method=>'POST',-action=>url_front($id),-enctype=>&CGI::URL_ENCODED);
			$html .= cgi->hidden('bookmark',$pos[0]);
			$html .= cgi->submit(-name=>'submit',-class=>'submit', -value => 'pause');
			$html .= cgi->end_form();
		$html .= ' ';
		$html .= @pos == 1 ? link_view($id,$pos[0] . ',' . $next_pos,'dualpage') : link_view($id, join(',',reverse @pos),'flip');
		$html .= ' ';
		$html .= cgi->a({href=>$ent[0]->page_url(),-class=>'extern'},'site');
		$html .= ' ';
		$html .= link_view($id,$next_pos,'next');
	$html .= cgi->end_div();
	$html .= cgi->end_html();
	Server::send_response($c,$html);
	Controller::add_hint(['view',$id,@pos]);
}

#$connection, $request, $id
#handles front requests
sub front {
	my ($c,$r,$id) = @_;
	Log->trace('handle front request');
	my $html = html_header('front',$id);
	if ($r->method eq 'POST') {
		my $cgi = cgi($r->content());
		if ($cgi->param('submit') eq 'pause') {
			UserPrefs->section('bookmark')->set($id,$cgi->param('bookmark'));
		}
		elsif ($cgi->param('submit') eq 'remove') {
			UserPrefs::remove('bookmark',$id);
		}
		UserPrefs::save();
		$html .= html_notification('updated');
		Controller::add_hint(['config']);
	}
	my $bm = UserPrefs->section('bookmark')->get($id);
	$html .= html_info(Cores::about($id));
	$html .= cgi->start_div({-class=>'navigation'});
		$html .= link_main();
		$html .= ' – ';
		$html .= link_view($id,1,'first');
		$html .= ' ';
		$html.= link_view($id,$bm,'Bookmark') if $bm;
		$html .= ' ';
		$html .= link_view($id,'*','last');
		if ($bm) {
			$html .= ' – ';
			$html .= cgi->start_form(-method=>'POST',-action=>url_front($id),-enctype=>&CGI::URL_ENCODED);
				$html .= cgi->submit(-name=>'submit',-class=>'submit', -value => 'remove');
			$html .= cgi->end_form();
		}
	$html .= cgi->end_div();
	my $col = Collection->get($id);
	my $ent = $bm ? $col->fetch($bm) : undef;
	if ($bm and $ent) {
		$html .= cgi->start_div({-class=>'content'});
			my $p = $col->fetch($bm-2);
			$html .= link_view($id,$bm-2,$p->html()) if $p;
			$p = $col->fetch($bm-1);
			$html .= link_view($id,$bm-1,$p->html()) if $p;
			$html .= link_view($id,$bm,$ent->html());
		$html .= cgi->end_div();
	}

	$html .= cgi->end_html();
	Server::send_response($c,$html);
	Controller::add_hint(['front',$id]);
}

#$connection, $request, @args
#handles action requests
sub search {
	my ($c,$r,@args) = @_;
	Log->trace('handle search request');
	my $cgi = cgi($r->url->query());
	my $query = $cgi->param('q');
	my $time = [gettimeofday];
	my @result = Cores::search(split /\s+/ , $query);
	my $html = html_header('search','search');
	$html .= cgi->start_div({-class=>'info'});
		$html .= "search for " . cgi->strong($query).cgi->br() . @result . ' results';
		$html .= cgi->br() . tv_interval($time) . ' seconds' . cgi->br();
		$html .= form_search($query);
	$html .= cgi->end_div();
	$html .= cgi->start_div({-class=>'navigation'});
		$html .= link_main();
	$html .= cgi->end_div();
	$html .= html_group($query,@result);
	$html .= cgi->end_html();
	Server::send_response($c,$html);
	return "search $query";
}

#$connection, $request
#handles tool requests
sub tools {
	my ($c,$r) = @_;
	Log->trace('handle tools');
	my $html = html_header('index','index');
	$html .= cgi->start_fieldset({-class=>'info'});
	$html .= cgi->legend('Tools');
	$html .= form_action('halt','halt');
	$html .= cgi->end_fieldset();
	$html .= cgi->start_div({-class=>'navigation'});
		$html .= link_main();
	$html .= cgi->end_div();
	$html .= cgi->end_html();
	Server::send_response($c,$html);
	return 'tools';
}

#$connection, $request
#handles blob requests
sub blob {
	my ($c,$r,$sha) = @_;
	Log->trace('handle blob');
	my $mtime =  HTTP::Date::str2time($r->header('If-Modified-Since'));
	my %stat = Cache::stat($sha);
	if (!$stat{size}) {
		Server::send_404($c);
	}
	elsif ($mtime and $stat{modified} <= $mtime) {
		$c->send_response(HTTP::Response->new( 304, 'Not Modified'));
	}
	else {
		my $res = HTTP::Response->new( 200, 'OK');
		my $blob = Cache::get($sha);
		if ($blob) {
			$res->content(${$blob});
			my $type = cgi($r->url->query())->param('type');
			$res->header('Content-Type'=>$type);
			$res->header('Content-Length'=>$stat{size});
			$res->header('Last-Modified'=>HTTP::Date::time2str($stat{modified}));
			$c->send_response($res);
		}
		else {
			Server::send_404($c);
		}
	}
	return 'blob';
}

1;
